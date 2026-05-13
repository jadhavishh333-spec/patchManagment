package com.patchmgmt.integration.execution;

import com.patchmgmt.entity.Server;
import com.patchmgmt.enums.OsType;
import com.patchmgmt.integration.model.ExecutionResult;
import com.patchmgmt.integration.model.ResolvedCredential;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * Demo/test mode execution strategy — simulates realistic patch execution output
 * without making any real network calls. Activated when integration.demo-mode=true.
 */
@Slf4j
public class MockExecutionStrategy implements RemoteExecutionStrategy {

    @Override
    public boolean supports(Server server) {
        return true; // Mock supports any server type
    }

    @Override
    public ExecutionResult execute(Server server, String command, ResolvedCredential credential) {
        log.info("[DEMO MODE] MockExecutionStrategy executing on {} ({})", server.getName(), server.getIpAddress());

        try {
            StringBuilder output = new StringBuilder();

            // Detect and simulate service lifecycle commands
            if (isServiceStopCommand(command)) {
                return simulateServiceStop(command, output);
            } else if (isServiceStartCommand(command)) {
                return simulateServiceStart(command, output);
            }

            // Standard patch execution simulation
            if (server.getOsType() == OsType.WINDOWS) {
                simulateWindows(server, command, output);
            } else {
                simulateLinux(server, command, output);
            }

            return ExecutionResult.success(output.toString());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExecutionResult.failure("Execution interrupted");
        } finally {
            credential.clearPassword();
        }
    }

    /**
     * Simulates file upload + install in demo mode — no real file transfer occurs.
     */
    @Override
    public ExecutionResult executeWithFile(Server server,
                                           String localFilePath,
                                           String remoteFilePath,
                                           String installCommand,
                                           ResolvedCredential credential) {
        log.info("[DEMO MODE] MockExecutionStrategy simulating file-upload+install on {} — {} → {}",
                 server.getName(), localFilePath, remoteFilePath);

        try {
            StringBuilder output = new StringBuilder();
            appendLog(output, "=== DEMO MODE: Manual File Patch ===");
            appendLog(output, "Local file  : " + localFilePath);
            appendLog(output, "Remote path : " + remoteFilePath);
            appendLog(output, "Install cmd : " + installCommand);
            Thread.sleep(300);

            if (server.getOsType() == OsType.WINDOWS) {
                appendLog(output, "Initiating WinRM connection to " + server.getIpAddress());
                Thread.sleep(400);
                appendLog(output, "Authenticated successfully");
                Thread.sleep(200);
                appendLog(output, "Creating remote directory: " + remoteFilePath.substring(0, remoteFilePath.lastIndexOf('\\')));
                Thread.sleep(200);
                appendLog(output, "[Transfer] Uploading patch binary via WinRM (chunked base64)...");
                Thread.sleep(800);
                appendLog(output, "[Transfer] File uploaded successfully → " + remoteFilePath);
                Thread.sleep(300);
                appendLog(output, "[Install]  Running: " + installCommand);
                Thread.sleep(1200);
                appendLog(output, "Installation progress: 25%...");
                Thread.sleep(400);
                appendLog(output, "Installation progress: 75%...");
                Thread.sleep(400);
                appendLog(output, "Installation progress: 100%");
                Thread.sleep(300);
                appendLog(output, "Patch installed successfully. Exit code: 0");
            } else {
                appendLog(output, "Establishing SSH connection to " + server.getIpAddress());
                Thread.sleep(300);
                appendLog(output, "SSH handshake complete");
                Thread.sleep(200);
                appendLog(output, "[Transfer] mkdir -p " + remoteFilePath.substring(0, remoteFilePath.lastIndexOf('/')));
                Thread.sleep(200);
                appendLog(output, "[Transfer] Streaming file via cat > " + remoteFilePath + " ...");
                Thread.sleep(600);
                appendLog(output, "[Transfer] File uploaded successfully → " + remoteFilePath);
                Thread.sleep(300);
                appendLog(output, "[Install]  $ " + installCommand);
                Thread.sleep(800);
                appendLog(output, "Selecting previously unselected package.");
                Thread.sleep(400);
                appendLog(output, "Unpacking package from " + remoteFilePath + " ...");
                Thread.sleep(400);
                appendLog(output, "Setting up package ...");
                Thread.sleep(300);
                appendLog(output, "Patch applied successfully. Exit code: 0");
            }

            return ExecutionResult.success(output.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExecutionResult.failure("Execution interrupted");
        } finally {
            credential.clearPassword();
        }
    }

    private void simulateWindows(Server server, String command, StringBuilder log) throws InterruptedException {
        appendLog(log, "Initiating WinRM connection to " + server.getIpAddress() + ":" + server.getWinrmPort());
        Thread.sleep(400);
        appendLog(log, "Authentication successful (user: " + "Administrator" + ")");
        Thread.sleep(200);
        appendLog(log, "Executing: " + command);
        Thread.sleep(600);
        appendLog(log, "Checking Windows Update service status...");
        Thread.sleep(300);
        appendLog(log, "Windows Update service is running");
        Thread.sleep(200);
        appendLog(log, "Verifying patch prerequisites...");
        Thread.sleep(400);
        appendLog(log, "Downloading patch package from WSUS...");
        Thread.sleep(800);
        appendLog(log, "Download complete (45.2 MB)");
        Thread.sleep(200);
        appendLog(log, "Installing patch — this may take several minutes...");
        Thread.sleep(1200);
        appendLog(log, "Installation progress: 25%...");
        Thread.sleep(400);
        appendLog(log, "Installation progress: 50%...");
        Thread.sleep(400);
        appendLog(log, "Installation progress: 75%...");
        Thread.sleep(400);
        appendLog(log, "Installation progress: 100%");
        Thread.sleep(300);
        appendLog(log, "Running post-install verification...");
        Thread.sleep(300);
        appendLog(log, "Patch installed successfully. Exit code: 0");
        appendLog(log, "Windows Update: SUCCESS");
    }

    private void simulateLinux(Server server, String command, StringBuilder log) throws InterruptedException {
        appendLog(log, "Establishing SSH connection to " + server.getIpAddress() + ":" + server.getSshPort());
        Thread.sleep(300);
        appendLog(log, "SSH handshake complete — authenticated as root");
        Thread.sleep(200);
        appendLog(log, "$ " + command);
        Thread.sleep(400);
        appendLog(log, "Hit:1 http://archive.ubuntu.com/ubuntu jammy InRelease");
        Thread.sleep(300);
        appendLog(log, "Get:2 http://security.ubuntu.com/ubuntu jammy-security InRelease [110 kB]");
        Thread.sleep(400);
        appendLog(log, "Fetched 2,304 kB in 2s (1,152 kB/s)");
        Thread.sleep(200);
        appendLog(log, "Reading package lists... Done");
        Thread.sleep(300);
        appendLog(log, "Building dependency tree... Done");
        Thread.sleep(200);
        appendLog(log, "Reading state information... Done");
        Thread.sleep(300);
        appendLog(log, "The following packages will be upgraded:");
        Thread.sleep(100);
        appendLog(log, "  openssl libssl3 libssl-dev");
        Thread.sleep(200);
        appendLog(log, "3 upgraded, 0 newly installed, 0 to remove and 0 not upgraded.");
        Thread.sleep(200);
        appendLog(log, "Need to get 2,456 kB of archives.");
        Thread.sleep(300);
        appendLog(log, "Fetching packages...");
        Thread.sleep(600);
        appendLog(log, "Unpacking openssl (3.0.2-0ubuntu1.15) over (3.0.2-0ubuntu1.14)");
        Thread.sleep(400);
        appendLog(log, "Setting up openssl (3.0.2-0ubuntu1.15)...");
        Thread.sleep(300);
        appendLog(log, "Processing triggers for man-db...");
        Thread.sleep(200);
        appendLog(log, "Patch applied successfully. Exit code: 0");
        appendLog(log, "Linux APT/YUM: SUCCESS");
    }

    private void appendLog(StringBuilder sb, String message) {
        sb.append("[").append(LocalDateTime.now()).append("] ").append(message).append("\n");
    }

    // ─── Service lifecycle detection & simulation ──────────────────────────────

    private boolean isServiceStopCommand(String cmd) {
        String lower = cmd.toLowerCase();
        return lower.contains("stop-webapppool") || lower.contains("stop-website")
            || lower.contains("systemctl stop") || lower.contains("service ") && lower.contains(" stop");
    }

    private boolean isServiceStartCommand(String cmd) {
        String lower = cmd.toLowerCase();
        return lower.contains("start-webapppool") || lower.contains("start-website")
            || lower.contains("systemctl start") || lower.contains("service ") && lower.contains(" start");
    }

    private ExecutionResult simulateServiceStop(String command, StringBuilder output) throws InterruptedException {
        String service = extractServiceName(command);
        appendLog(output, "[DEMO] Executing service stop command: " + command);
        Thread.sleep(300);
        appendLog(output, "[DEMO] Stopping " + service + "...");
        Thread.sleep(400);
        appendLog(output, "[DEMO] " + service + " stopped successfully.");
        return ExecutionResult.success(output.toString());
    }

    private ExecutionResult simulateServiceStart(String command, StringBuilder output) throws InterruptedException {
        String service = extractServiceName(command);
        appendLog(output, "[DEMO] Executing service start command: " + command);
        Thread.sleep(300);
        appendLog(output, "[DEMO] Starting " + service + "...");
        Thread.sleep(500);
        appendLog(output, "[DEMO] " + service + " started successfully.");
        appendLog(output, "[DEMO] Service is now running and accepting connections.");
        return ExecutionResult.success(output.toString());
    }

    /** Best-effort extraction of the service name from a lifecycle command. */
    private String extractServiceName(String command) {
        // PowerShell: -Name 'value'
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("-Name\\s+'([^']+)'", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(command);
        if (m.find()) return m.group(1);
        // systemctl: systemctl stop <name>
        m = java.util.regex.Pattern
            .compile("systemctl\\s+(?:stop|start)\\s+(\\S+)")
            .matcher(command);
        if (m.find()) return m.group(1);
        return "service";
    }
}
