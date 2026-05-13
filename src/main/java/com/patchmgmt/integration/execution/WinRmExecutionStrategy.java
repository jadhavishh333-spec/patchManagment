package com.patchmgmt.integration.execution;

import com.patchmgmt.config.IntegrationProperties;
import com.patchmgmt.entity.Server;
import com.patchmgmt.enums.OsType;
import com.patchmgmt.integration.model.ExecutionResult;
import com.patchmgmt.integration.model.ResolvedCredential;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Executes PowerShell commands on Windows servers via WinRM (HTTP/HTTPS).
 * Requires WinRM to be enabled and configured on the target server:
 *   winrm quickconfig
 *   winrm set winrm/config/service/auth @{Basic="true"}
 */
@Slf4j
@RequiredArgsConstructor
public class WinRmExecutionStrategy implements RemoteExecutionStrategy {

    private final IntegrationProperties props;

    /** Chunk size in raw bytes for base64 file transfer (≈ 44 KB base64 per chunk). */
    private static final int UPLOAD_CHUNK_BYTES = 32_768;

    @Override
    public boolean supports(Server server) {
        return server.getOsType() == OsType.WINDOWS;
    }

    @Override
    public ExecutionResult execute(Server server, String command, ResolvedCredential credential) {
        log.info("WinRM executing on {} ({}) — command length: {}", server.getName(), server.getIpAddress(), command.length());

        int port = server.getWinrmPort() != null ? server.getWinrmPort() : props.getWinrm().getPort();
        String protocol = props.getWinrm().isUseHttps() ? "https" : "http";

        // Build PowerShell remote command via winrs.exe (native on Windows) or winrm client
        // For cross-platform compatibility we use ProcessBuilder with PowerShell Invoke-Command
        String psCommand = String.format(
            "powershell.exe -NonInteractive -Command \"" +
            "$pw = ConvertTo-SecureString '%s' -AsPlainText -Force;" +
            "$cred = New-Object System.Management.Automation.PSCredential('%s', $pw);" +
            "Invoke-Command -ComputerName %s -Port %d -Credential $cred -ScriptBlock { %s }\"",
            credential.passwordAsString().replace("'", "''"),
            credential.username(),
            server.getIpAddress(),
            port,
            command
        );

        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", psCommand);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(props.getWinrm().getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ExecutionResult.failure("WinRM execution timed out after " + props.getWinrm().getTimeoutSeconds() + "s");
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return ExecutionResult.success(output.toString());
            } else {
                return ExecutionResult.failure(output.toString(), exitCode);
            }

        } catch (Exception e) {
            log.error("WinRM execution failed for server {}: {}", server.getName(), e.getMessage());
            return ExecutionResult.failure("WinRM exception: " + e.getMessage());
        } finally {
            credential.clearPassword();
        }
    }

    /**
     * Transfers a local patch file to a Windows target via WinRM and then
     * executes the install command.
     *
     * <p><b>Transfer mechanism:</b>
     * The file is read in {@value #UPLOAD_CHUNK_BYTES}-byte chunks, base64-encoded,
     * and pushed via individual {@code Invoke-Command} calls that use
     * {@code [System.IO.File]::WriteAllBytes} (first chunk) and a binary-append
     * FileStream (subsequent chunks) to reconstruct the file on the target.
     * This avoids any SMB / WinRM file-copy dependency and works across
     * network segments where only port 5985 / 5986 is open.
     *
     * <p><b>For large files (&gt; ~50 MB)</b> you may wish to pre-stage the file
     * via a share path and pass it directly as {@code installCommand}.
     */
    @Override
    public ExecutionResult executeWithFile(Server server,
                                           String localFilePath,
                                           String remoteFilePath,
                                           String installCommand,
                                           ResolvedCredential credential) {
        log.info("WinRM file-upload+install on {} — local: {} → remote: {}",
                 server.getName(), localFilePath, remoteFilePath);

        int  port           = server.getWinrmPort() != null ? server.getWinrmPort() : props.getWinrm().getPort();
        long timeoutSeconds = props.getWinrm().getTimeoutSeconds();
        String escapedPw   = credential.passwordAsString().replace("'", "''");
        String username    = credential.username();
        String host        = server.getIpAddress();

        java.io.File localFile = new java.io.File(localFilePath);
        if (!localFile.exists()) {
            return ExecutionResult.failure("Local patch file not found: " + localFilePath);
        }

        StringBuilder masterOutput = new StringBuilder();

        try {
            byte[] fileBytes = java.nio.file.Files.readAllBytes(localFile.toPath());
            int totalChunks = (int) Math.ceil((double) fileBytes.length / UPLOAD_CHUNK_BYTES);
            log.info("Uploading {} bytes in {} chunk(s) to {}:{}", fileBytes.length, totalChunks, host, remoteFilePath);

            // ── Step 1: ensure remote directory exists ──────────────────────
            String remoteDir = remoteFilePath.contains("\\")
                ? remoteFilePath.substring(0, remoteFilePath.lastIndexOf('\\'))
                : remoteFilePath.substring(0, remoteFilePath.lastIndexOf('/'));

            String mkdirScript = String.format(
                "if (-not (Test-Path '%s')) { New-Item -ItemType Directory -Path '%s' -Force | Out-Null }",
                remoteDir, remoteDir);
            ExecutionResult mkdirResult = runRemotePs(mkdirScript, host, port, username, escapedPw, timeoutSeconds);
            if (!mkdirResult.success()) {
                return ExecutionResult.failure("Failed to create remote directory: " + mkdirResult.output());
            }
            masterOutput.append("[Transfer] Remote directory ready: ").append(remoteDir).append("\n");

            // ── Step 2: transfer file in chunks ─────────────────────────────
            for (int i = 0; i < totalChunks; i++) {
                int from  = i * UPLOAD_CHUNK_BYTES;
                int len   = Math.min(UPLOAD_CHUNK_BYTES, fileBytes.length - from);
                byte[] chunk = java.util.Arrays.copyOfRange(fileBytes, from, from + len);
                String b64  = java.util.Base64.getEncoder().encodeToString(chunk);

                String chunkScript;
                if (i == 0) {
                    // First chunk → create / overwrite the file
                    chunkScript = String.format(
                        "$bytes = [System.Convert]::FromBase64String('%s');" +
                        "[System.IO.File]::WriteAllBytes('%s', $bytes)",
                        b64, remoteFilePath);
                } else {
                    // Subsequent chunks → append
                    chunkScript = String.format(
                        "$bytes = [System.Convert]::FromBase64String('%s');" +
                        "$stream = [System.IO.File]::Open('%s'," +
                        "  [System.IO.FileMode]::Append," +
                        "  [System.IO.FileAccess]::Write);" +
                        "$stream.Write($bytes, 0, $bytes.Length);" +
                        "$stream.Close()",
                        b64, remoteFilePath);
                }

                ExecutionResult chunkResult = runRemotePs(chunkScript, host, port, username, escapedPw, timeoutSeconds);
                if (!chunkResult.success()) {
                    return ExecutionResult.failure(
                        "File transfer failed at chunk " + (i + 1) + "/" + totalChunks + ": " + chunkResult.output());
                }

                if ((i + 1) % 10 == 0 || i == totalChunks - 1) {
                    log.info("Upload progress: {}/{} chunks", i + 1, totalChunks);
                }
            }

            masterOutput.append("[Transfer] File transferred (")
                        .append(fileBytes.length).append(" bytes) → ").append(remoteFilePath).append("\n");

            // ── Step 3: run install command ─────────────────────────────────
            masterOutput.append("[Install] Running: ").append(installCommand).append("\n");
            ExecutionResult installResult = runRemotePs(installCommand, host, port, username, escapedPw, timeoutSeconds);
            masterOutput.append(installResult.output() != null ? installResult.output() : "");

            if (installResult.success()) {
                return ExecutionResult.success(masterOutput.toString());
            } else {
                return ExecutionResult.failure(masterOutput.toString(), installResult.exitCode());
            }

        } catch (Exception e) {
            log.error("WinRM file-upload failed for server {}: {}", server.getName(), e.getMessage());
            return ExecutionResult.failure("WinRM file-upload exception: " + e.getMessage());
        } finally {
            credential.clearPassword();
        }
    }

    // ── Internal helper ────────────────────────────────────────────────────────

    private ExecutionResult runRemotePs(String script, String host, int port,
                                        String username, String escapedPw, long timeoutSeconds) {
        String psCommand = String.format(
            "powershell.exe -NonInteractive -Command \"" +
            "$pw = ConvertTo-SecureString '%s' -AsPlainText -Force;" +
            "$cred = New-Object System.Management.Automation.PSCredential('%s', $pw);" +
            "Invoke-Command -ComputerName %s -Port %d -Credential $cred -ScriptBlock { %s }\"",
            escapedPw, username, host, port, script);

        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", psCommand);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append("\n");
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ExecutionResult.failure("WinRM timed out after " + timeoutSeconds + "s");
            }
            int exitCode = process.exitValue();
            return exitCode == 0 ? ExecutionResult.success(output.toString())
                                 : ExecutionResult.failure(output.toString(), exitCode);
        } catch (Exception e) {
            return ExecutionResult.failure("WinRM runRemotePs exception: " + e.getMessage());
        }
    }
}
