package com.patchmgmt.integration.execution;

import com.patchmgmt.entity.Server;
import com.patchmgmt.enums.IisStopMode;
import com.patchmgmt.enums.OsType;
import com.patchmgmt.integration.model.ExecutionResult;
import com.patchmgmt.integration.model.ResolvedCredential;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Manages the pre-patch service stop and post-patch service start lifecycle.
 *
 * <p>Behaviour:
 * <ul>
 *   <li><b>Stop</b>: Each service is stopped once. Failures are logged as warnings
 *       (they do <em>not</em> abort the patch).</li>
 *   <li><b>Start</b>: Each service is retried up to {@value #MAX_START_RETRIES} times
 *       with a {@value #RETRY_DELAY_MS} ms delay between attempts. If all retries
 *       are exhausted, a warning is logged but the patch job stays COMPLETED.</li>
 * </ul>
 *
 * <p>Windows: uses PowerShell IIS WebAdministration commandlets (Stop/Start-WebAppPool,
 * Stop/Start-Website) via WinRM.
 * <p>Linux: uses {@code systemctl stop/start <service>} via SSH.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceLifecycleManager {

    private static final int MAX_START_RETRIES = 3;
    private static final long RETRY_DELAY_MS   = 5_000; // 5 s between restart attempts

    private final RemoteExecutionStrategy executionStrategy;

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Stop all configured services on the target server before patching.
     * Call this before the patch command is executed.
     */
    public void stopServices(Server server, ResolvedCredential credential, StringBuilder masterLog) {
        List<String> services = parseServices(server.getPreStopServices());
        if (services.isEmpty()) {
            appendLog(masterLog, "[PRE-PATCH] No services configured for pre-patch stop — skipping.");
            return;
        }

        appendLog(masterLog, "[PRE-PATCH] ══════════════════════════════════════════════════");
        appendLog(masterLog, "[PRE-PATCH] Stopping " + services.size() + " service(s) before patching...");

        for (String service : services) {
            String cmd = buildStopCommand(server, service, credential);
            appendLog(masterLog, "[PRE-PATCH] Stopping: " + service);
            appendLog(masterLog, "[PRE-PATCH] Command : " + cmd);
            try {
                ExecutionResult result = executionStrategy.execute(server, cmd, credential);
                if (result.success()) {
                    appendLog(masterLog, "[PRE-PATCH] OK — " + service + " stopped.");
                } else {
                    appendLog(masterLog, "[PRE-PATCH] WARNING — Could not stop " + service
                            + " (exit " + result.exitCode() + "): " + result.error());
                    log.warn("[ServiceLifecycle] Failed to stop service '{}' on {}: {}",
                            service, server.getName(), result.error());
                }
            } catch (Exception e) {
                appendLog(masterLog, "[PRE-PATCH] WARNING — Exception stopping " + service + ": " + e.getMessage());
                log.warn("[ServiceLifecycle] Exception stopping service '{}' on {}", service, server.getName(), e);
            }
        }
        appendLog(masterLog, "[PRE-PATCH] ══════════════════════════════════════════════════");
    }

    /**
     * Start all configured services on the target server after patching.
     * Retries up to {@value #MAX_START_RETRIES} times per service before giving up.
     * Call this after the patch command finishes (regardless of patch success/failure).
     */
    public void startServices(Server server, ResolvedCredential credential, StringBuilder masterLog) {
        List<String> services = parseServices(server.getPreStopServices());
        if (services.isEmpty()) {
            return; // nothing to do
        }

        appendLog(masterLog, "[POST-PATCH] ═════════════════════════════════════════════════");
        appendLog(masterLog, "[POST-PATCH] Starting " + services.size() + " service(s) after patching...");

        for (String service : services) {
            startWithRetry(server, service, credential, masterLog);
        }
        appendLog(masterLog, "[POST-PATCH] ═════════════════════════════════════════════════");
    }

    // ─── Internal ──────────────────────────────────────────────────────────────

    private void startWithRetry(Server server, String service,
                                ResolvedCredential credential, StringBuilder masterLog) {
        String cmd = buildStartCommand(server, service, credential);
        appendLog(masterLog, "[POST-PATCH] Starting: " + service);
        appendLog(masterLog, "[POST-PATCH] Command : " + cmd);

        for (int attempt = 1; attempt <= MAX_START_RETRIES; attempt++) {
            try {
                if (attempt > 1) {
                    appendLog(masterLog, "[POST-PATCH] Retry " + attempt + "/" + MAX_START_RETRIES
                            + " for service: " + service);
                    Thread.sleep(RETRY_DELAY_MS);
                }

                ExecutionResult result = executionStrategy.execute(server, cmd, credential);
                if (result.success()) {
                    appendLog(masterLog, "[POST-PATCH] OK — " + service + " started successfully"
                            + (attempt > 1 ? " on attempt " + attempt : "") + ".");
                    log.info("[ServiceLifecycle] Service '{}' started on {} (attempt {})",
                            service, server.getName(), attempt);
                    return; // success — move to next service
                }

                appendLog(masterLog, "[POST-PATCH] Attempt " + attempt + " FAILED for " + service
                        + " (exit " + result.exitCode() + "): " + result.error());
                log.warn("[ServiceLifecycle] Attempt {}/{} failed to start '{}' on {}: {}",
                        attempt, MAX_START_RETRIES, service, server.getName(), result.error());

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                appendLog(masterLog, "[POST-PATCH] WARNING — Interrupted while waiting to retry " + service);
                break;
            } catch (Exception e) {
                appendLog(masterLog, "[POST-PATCH] Attempt " + attempt + " exception for " + service
                        + ": " + e.getMessage());
                log.warn("[ServiceLifecycle] Exception on attempt {}/{} starting '{}' on {}",
                        attempt, MAX_START_RETRIES, service, server.getName(), e);
            }
        }

        // All retries exhausted
        appendLog(masterLog, "[POST-PATCH] WARNING — Could not start '" + service
                + "' after " + MAX_START_RETRIES + " attempts. Manual intervention required.");
        log.error("[ServiceLifecycle] FAILED to start '{}' on {} after {} retries — manual action needed!",
                service, server.getName(), MAX_START_RETRIES);
    }

    // ─── Command Builders ──────────────────────────────────────────────────────

    private String buildStopCommand(Server server, String service, ResolvedCredential credential) {
        if (server.getOsType() == OsType.WINDOWS) {
            return buildWindowsStopCommand(service, server.getIisStopMode());
        } else {
            return buildLinuxStopCommand(service, credential);
        }
    }

    private String buildStartCommand(Server server, String service, ResolvedCredential credential) {
        if (server.getOsType() == OsType.WINDOWS) {
            return buildWindowsStartCommand(service, server.getIisStopMode());
        } else {
            return buildLinuxStartCommand(service, credential);
        }
    }

    /** PowerShell via WinRM — stop App Pool, Site, or Both */
    private String buildWindowsStopCommand(String service, IisStopMode mode) {
        if (mode == null) mode = IisStopMode.APPPOOL;
        return switch (mode) {
            case APPPOOL -> "Import-Module WebAdministration; Stop-WebAppPool -Name '" + service + "'";
            case SITE    -> "Import-Module WebAdministration; Stop-Website -Name '" + service + "'";
            case BOTH    -> "Import-Module WebAdministration; "
                          + "Stop-Website -Name '" + service + "'; "
                          + "Stop-WebAppPool -Name '" + service + "'";
        };
    }

    private String buildWindowsStartCommand(String service, IisStopMode mode) {
        if (mode == null) mode = IisStopMode.APPPOOL;
        return switch (mode) {
            case APPPOOL -> "Import-Module WebAdministration; Start-WebAppPool -Name '" + service + "'";
            case SITE    -> "Import-Module WebAdministration; Start-Website -Name '" + service + "'";
            case BOTH    -> "Import-Module WebAdministration; "
                          + "Start-WebAppPool -Name '" + service + "'; "
                          + "Start-Website -Name '" + service + "'";
        };
    }

    /** systemctl via SSH — runs under sudo -S so no interactive TTY is needed */
    private String buildLinuxStopCommand(String service, ResolvedCredential credential) {
        String password = credential.passwordAsString().replace("'", "'\\''")
;
        return "echo '" + password + "' | sudo -S sh -c "
             + "'systemctl stop " + service + " 2>/dev/null || service " + service + " stop'";
    }

    private String buildLinuxStartCommand(String service, ResolvedCredential credential) {
        String password = credential.passwordAsString().replace("'", "'\\''")
;
        return "echo '" + password + "' | sudo -S sh -c "
             + "'systemctl start " + service + " 2>/dev/null || service " + service + " start'";
    }

    // ─── Utilities ─────────────────────────────────────────────────────────────

    /**
     * Parses the comma-separated preStopServices string into a trimmed, non-empty list.
     */
    private List<String> parseServices(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private void appendLog(StringBuilder sb, String message) {
        sb.append("[").append(LocalDateTime.now()).append("] ").append(message).append("\n");
    }
}
