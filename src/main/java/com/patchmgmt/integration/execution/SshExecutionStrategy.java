package com.patchmgmt.integration.execution;

import com.patchmgmt.config.IntegrationProperties;
import com.patchmgmt.entity.Server;
import com.patchmgmt.enums.OsType;
import com.patchmgmt.integration.model.ExecutionResult;
import com.patchmgmt.integration.model.ResolvedCredential;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Executes shell commands on Linux servers via SSH using Apache Sshd.
 * Requires SSH key or password auth — credentials fetched from CyberArk.
 */
@Slf4j
@RequiredArgsConstructor
public class SshExecutionStrategy implements RemoteExecutionStrategy {

    private final IntegrationProperties props;

    @Override
    public boolean supports(Server server) {
        return server.getOsType() == OsType.LINUX;
    }

    @Override
    public ExecutionResult execute(Server server, String command, ResolvedCredential credential) {
        log.info("SSH executing on {} ({}) — command: {}", server.getName(), server.getIpAddress(), command);

        int port = server.getSshPort() != null ? server.getSshPort() : props.getSsh().getPort();
        long timeoutMillis = TimeUnit.SECONDS.toMillis(props.getSsh().getTimeoutSeconds());

        SshClient client = SshClient.setUpDefaultClient();
        try {
            client.start();

            try (ClientSession session = client.connect(credential.username(), server.getIpAddress(), port)
                    .verify(timeoutMillis).getSession()) {

                session.addPasswordIdentity(credential.passwordAsString());
                session.auth().verify(timeoutMillis);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ByteArrayOutputStream err = new ByteArrayOutputStream();

                try (ClientChannel channel = session.createExecChannel(command)) {
                    channel.setOut(out);
                    channel.setErr(err);
                    channel.open().verify(timeoutMillis);
                    channel.waitFor(java.util.EnumSet.of(ClientChannelEvent.CLOSED), timeoutMillis);

                    Integer exitStatus = channel.getExitStatus();
                    String output = out.toString(StandardCharsets.UTF_8);
                    String error  = err.toString(StandardCharsets.UTF_8);

                    if (exitStatus == null || exitStatus == 0) {
                        return ExecutionResult.success(output);
                    } else {
                        return ExecutionResult.failure(error.isEmpty() ? output : error, exitStatus);
                    }
                }
            }
        } catch (Exception e) {
            log.error("SSH execution failed for server {}: {}", server.getName(), e.getMessage());
            return ExecutionResult.failure("SSH exception: " + e.getMessage());
        } finally {
            client.stop();
        }
    }

    /**
     * Uploads a local patch binary to the Linux target via SSH (using {@code cat > remotePath}
     * piped through the exec channel's stdin), then runs the install command.
     *
     * <p>Why {@code cat >}? It works with vanilla SSH without requiring SCP or SFTP subsystem.
     * For large files the upload is streamed — no full file buffering in memory.
     */
    @Override
    public ExecutionResult executeWithFile(Server server,
                                           String localFilePath,
                                           String remoteFilePath,
                                           String installCommand,
                                           ResolvedCredential credential) {
        log.info("SSH file-upload+install on {} — local: {} → remote: {}",
                 server.getName(), localFilePath, remoteFilePath);

        int  port          = server.getSshPort() != null ? server.getSshPort() : props.getSsh().getPort();
        long timeoutMillis = TimeUnit.SECONDS.toMillis(props.getSsh().getTimeoutSeconds());

        SshClient client = SshClient.setUpDefaultClient();
        try {
            client.start();

            try (ClientSession session = client.connect(credential.username(), server.getIpAddress(), port)
                    .verify(timeoutMillis).getSession()) {

                session.addPasswordIdentity(credential.passwordAsString());
                session.auth().verify(timeoutMillis);

                // ── Step 1: ensure remote directory exists ──────────────────
                String remoteDir = remoteFilePath.contains("/")
                    ? remoteFilePath.substring(0, remoteFilePath.lastIndexOf('/'))
                    : ".";
                ByteArrayOutputStream mkdirOut = new ByteArrayOutputStream();
                ByteArrayOutputStream mkdirErr = new ByteArrayOutputStream();
                try (ClientChannel mkdirCh = session.createExecChannel("mkdir -p " + remoteDir)) {
                    mkdirCh.setOut(mkdirOut);
                    mkdirCh.setErr(mkdirErr);
                    mkdirCh.open().verify(timeoutMillis);
                    mkdirCh.waitFor(java.util.EnumSet.of(ClientChannelEvent.CLOSED), timeoutMillis);
                }
                log.debug("mkdir -p {} → done", remoteDir);

                // ── Step 2: stream file via cat > remoteFilePath ────────────
                java.io.File localFile = new java.io.File(localFilePath);
                if (!localFile.exists()) {
                    return ExecutionResult.failure("Local patch file not found: " + localFilePath);
                }

                ByteArrayOutputStream catOut = new ByteArrayOutputStream();
                ByteArrayOutputStream catErr = new ByteArrayOutputStream();
                try (ClientChannel catCh = session.createExecChannel("cat > " + remoteFilePath);
                     java.io.FileInputStream fis = new java.io.FileInputStream(localFile)) {
                    catCh.setIn(fis);
                    catCh.setOut(catOut);
                    catCh.setErr(catErr);
                    catCh.open().verify(timeoutMillis);
                    // Use a longer timeout for the upload (file transfer)
                    long uploadTimeout = Math.max(timeoutMillis, localFile.length() / 50_000L + timeoutMillis);
                    catCh.waitFor(java.util.EnumSet.of(ClientChannelEvent.CLOSED), uploadTimeout);
                }
                log.info("File uploaded ({} bytes) to {}:{}", localFile.length(), server.getIpAddress(), remoteFilePath);

                // ── Step 2.5: strip Windows CRLF from shell scripts ─────────
                // Files edited on Windows contain \r\n line endings which cause
                // "command not found" errors on Linux. Strip them right after upload.
                boolean isShellScript = remoteFilePath.toLowerCase().endsWith(".sh");
                if (isShellScript) {
                    ByteArrayOutputStream dosOut = new ByteArrayOutputStream();
                    ByteArrayOutputStream dosErr = new ByteArrayOutputStream();
                    String dosCmd = "sed -i 's/\\r//' " + remoteFilePath;
                    try (ClientChannel dosCh = session.createExecChannel(dosCmd)) {
                        dosCh.setOut(dosOut);
                        dosCh.setErr(dosErr);
                        dosCh.open().verify(timeoutMillis);
                        dosCh.waitFor(java.util.EnumSet.of(ClientChannelEvent.CLOSED), timeoutMillis);
                    }
                    log.debug("CRLF strip done on {}", remoteFilePath);
                }

                // ── Step 3: run install command ─────────────────────────────
                // For shell scripts we run with `echo 'pw' | sudo -S bash <file>`
                // so that any `sudo` call inside the script succeeds without a TTY.
                // (SSH exec channels are non-interactive; sudo cannot prompt for a password.)
                String effectiveCmd;
                if (isShellScript && !installCommand.contains("sudo -S")) {
                    String escapedPw = credential.passwordAsString().replace("'", "'\\''");
                    effectiveCmd = "echo '" + escapedPw + "' | sudo -S bash " + remoteFilePath;
                    log.info("Running shell script under sudo -S: {}", remoteFilePath);
                } else {
                    effectiveCmd = installCommand;
                }

                ByteArrayOutputStream installOut = new ByteArrayOutputStream();
                ByteArrayOutputStream installErr = new ByteArrayOutputStream();
                try (ClientChannel installCh = session.createExecChannel(effectiveCmd)) {
                    installCh.setOut(installOut);
                    installCh.setErr(installErr);
                    installCh.open().verify(timeoutMillis);
                    installCh.waitFor(java.util.EnumSet.of(ClientChannelEvent.CLOSED), timeoutMillis);

                    Integer exitStatus = installCh.getExitStatus();
                    String output = installOut.toString(StandardCharsets.UTF_8);
                    String error  = installErr.toString(StandardCharsets.UTF_8);

                    // Filter out the sudo password-prompt noise from stderr
                    String filteredErr = error.lines()
                        .filter(l -> !l.startsWith("[sudo]") && !l.contains("password for"))
                        .collect(java.util.stream.Collectors.joining("\n"));

                    String combined = "[Upload] File transferred to " + remoteFilePath + "\n"
                                    + "[Install] " + installCommand + "\n"
                                    + output + (filteredErr.isBlank() ? "" : "\nSTDERR: " + filteredErr);

                    if (exitStatus == null || exitStatus == 0) {
                        return ExecutionResult.success(combined);
                    } else {
                        return ExecutionResult.failure(combined, exitStatus);
                    }
                }
            }
        } catch (Exception e) {
            log.error("SSH file-upload failed for server {}: {}", server.getName(), e.getMessage());
            return ExecutionResult.failure("SSH file-upload exception: " + e.getMessage());
        } finally {
            client.stop();
        }
    }
}
