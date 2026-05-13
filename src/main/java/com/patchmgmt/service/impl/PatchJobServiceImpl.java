package com.patchmgmt.service.impl;

import com.patchmgmt.dto.PatchJobDto;
import com.patchmgmt.entity.*;
import com.patchmgmt.enums.ComplianceStatus;
import com.patchmgmt.enums.PatchStatus;
import com.patchmgmt.exception.ResourceNotFoundException;
import com.patchmgmt.exception.ValidationException;
import com.patchmgmt.integration.credential.CredentialProvider;
import com.patchmgmt.integration.execution.RemoteExecutionStrategy;
import com.patchmgmt.integration.execution.ServiceLifecycleManager;
import com.patchmgmt.integration.model.ExecutionResult;
import com.patchmgmt.integration.model.ResolvedCredential;
import com.patchmgmt.repository.*;
import com.patchmgmt.service.AuditLogService;
import com.patchmgmt.service.ComplianceService;
import com.patchmgmt.service.PatchJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
// NOTE: @Transactional is NOT at class level intentionally.
// Class-level @Transactional conflicts with @Async — the TX advisor in the calling thread
// prevents the async thread from starting its own transaction correctly.
// Each method has its own @Transactional where needed.
public class PatchJobServiceImpl implements PatchJobService {

    private final PatchJobRepository patchJobRepository;
    private final ServerRepository serverRepository;
    private final PatchRepository patchRepository;
    private final UserRepository userRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final AuditLogService auditLogService;
    private final ComplianceService complianceService;
    private final CredentialProvider credentialProvider;
    private final RemoteExecutionStrategy remoteExecutionStrategy;
    private final ServiceLifecycleManager serviceLifecycleManager;

    /** Same default as PatchServiceImpl — used to locate migrated/moved patch files. */
    @org.springframework.beans.factory.annotation.Value("${patch.upload.dir:./patch-uploads}")
    private String uploadDir;

    /* ─── Create ──────────────────────────────────────────────────────────── */
    @Override
    @Transactional
    public PatchJob create(PatchJobDto dto, String createdBy) {
        Server server = serverRepository.findById(dto.getServerId())
            .orElseThrow(() -> new ResourceNotFoundException("Server", dto.getServerId()));
        Patch patch = patchRepository.findById(dto.getPatchId())
            .orElseThrow(() -> new ResourceNotFoundException("Patch", dto.getPatchId()));
        AppUser user = userRepository.findByUsername(createdBy)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + createdBy));

        PatchJob job = PatchJob.builder()
            .title(dto.getTitle())
            .server(server)
            .patch(patch)
            .status(PatchStatus.PENDING)
            .scheduledAt(dto.getScheduledAt())
            .maxRetries(3)
            .timeoutMinutes(30)
            .createdBy(user)
            .build();
        PatchJob saved = patchJobRepository.save(job);
        auditLogService.log("CREATE_JOB", "PatchJob", saved.getId(),
            "Created patch job: " + dto.getTitle() + " for server: " + server.getName(), createdBy);
        log.info("Patch job created: id={} title={} server={}", saved.getId(), saved.getTitle(), server.getName());
        return saved;
    }

    /* ─── Approve ─────────────────────────────────────────────────────────── */
    @Override
    @Transactional
    public PatchJob approve(Long id, String approvedBy) {
        PatchJob job = patchJobRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PatchJob", id));
        if (job.getStatus() != PatchStatus.PENDING)
            throw new ValidationException("Only PENDING jobs can be approved. Current status: " + job.getStatus());

        AppUser approver = userRepository.findByUsername(approvedBy).orElse(null);
        job.setStatus(PatchStatus.APPROVED);
        job.setApprovedBy(approver);
        job.setApprovedAt(LocalDateTime.now());
        PatchJob saved = patchJobRepository.save(job);
        auditLogService.log("APPROVE_JOB", "PatchJob", id, "Approved by: " + approvedBy, approvedBy);
        log.info("Job {} approved by {}", id, approvedBy);
        return saved;
    }

    /* ─── Cancel ──────────────────────────────────────────────────────────── */
    @Override
    @Transactional
    public PatchJob cancel(Long id) {
        PatchJob job = patchJobRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PatchJob", id));
        if (job.getStatus() == PatchStatus.IN_PROGRESS || job.getStatus() == PatchStatus.COMPLETED)
            throw new ValidationException("Cannot cancel a job that is IN_PROGRESS or COMPLETED");
        job.setStatus(PatchStatus.CANCELLED);
        auditLogService.log("CANCEL_JOB", "PatchJob", id, "Job cancelled", "SYSTEM");
        return patchJobRepository.save(job);
    }

    /* ─── Delete ──────────────────────────────────────────────────────────── */
    @Override
    @Transactional
    public void delete(Long id) {
        PatchJob job = patchJobRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PatchJob", id));
        if (job.getStatus() == PatchStatus.IN_PROGRESS)
            throw new ValidationException("Cannot delete a job that is currently IN_PROGRESS.");

        String jobTitle = job.getTitle();
        // Delete child execution logs first (avoids FK constraint violation)
        executionLogRepository.deleteByPatchJobId(id);
        patchJobRepository.deleteById(id);

        auditLogService.log("DELETE_JOB", "PatchJob", id,
            "Job '" + jobTitle + "' permanently deleted", "ADMIN");
        log.info("Patch job {} ('{}') deleted — execution logs cascade-removed", id, jobTitle);
    }

    /* ─── Retry ───────────────────────────────────────────────────────────── */
    @Override
    @Transactional
    public PatchJob retryJob(Long id, String requestedBy) {
        PatchJob job = patchJobRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PatchJob", id));
        if (job.getStatus() != PatchStatus.FAILED)
            throw new ValidationException("Only FAILED jobs can be retried");
        if (job.getRetryCount() >= job.getMaxRetries())
            throw new ValidationException("Max retries (" + job.getMaxRetries() + ") already reached");

        job.setStatus(PatchStatus.APPROVED);
        job.setRetryCount(job.getRetryCount() + 1);
        PatchJob saved = patchJobRepository.save(job);
        auditLogService.log("RETRY_JOB", "PatchJob", id,
            "Retry " + job.getRetryCount() + "/" + job.getMaxRetries() + " requested by: " + requestedBy, requestedBy);
        log.info("Job {} retry {} of {} requested by {} — set APPROVED, scheduler/manual execute will pick up",
            id, job.getRetryCount(), job.getMaxRetries(), requestedBy);
        // NOTE: do NOT call execute(id) here — that bypasses the @Async proxy and runs
        // synchronously inside this transaction. Let the caller (controller) trigger execute
        // through the Spring proxy, or let the scheduler pick it up.
        return saved;
    }

    /* ─── Execute ─────────────────────────────────────────────────────────── */
    @Override
    @Async("patchExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(Long id) {
        // Re-fetch inside the new thread's transaction
        PatchJob job = patchJobRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PatchJob", id));

        if (job.getStatus() != PatchStatus.APPROVED && job.getStatus() != PatchStatus.RETRYING) {
            log.warn("Job {} is not in APPROVED state ({}), skipping execution", id, job.getStatus());
            return;
        }

        Server server = job.getServer();
        Patch  patch  = job.getPatch();
        StringBuilder masterLog = new StringBuilder();

        // Mark as IN_PROGRESS
        job.setStatus(PatchStatus.IN_PROGRESS);
        job.setStartedAt(LocalDateTime.now());
        patchJobRepository.save(job);

        masterLog.append(logLine("═══════════════════════════════════════════════════════"));
        masterLog.append(logLine("PATCH JOB EXECUTION STARTED"));
        masterLog.append(logLine("Job ID   : " + job.getId()));
        masterLog.append(logLine("Title    : " + job.getTitle()));
        masterLog.append(logLine("Server   : " + server.getName() + " (" + server.getIpAddress() + ")"));
        masterLog.append(logLine("OS       : " + server.getOsType()));
        masterLog.append(logLine("Patch    : " + patch.getTitle() + " [" + patch.getPatchId() + "]"));
        masterLog.append(logLine("Severity : " + patch.getSeverity()));
        masterLog.append(logLine("Retry    : " + job.getRetryCount() + "/" + job.getMaxRetries()));
        masterLog.append(logLine("═══════════════════════════════════════════════════════"));

        // Create execution log entry
        ExecutionLog exLog = ExecutionLog.builder()
            .patchJob(job)
            .server(server)
            .status(PatchStatus.IN_PROGRESS)
            .startedAt(LocalDateTime.now())
            .retryAttempt(job.getRetryCount())
            .build();
        executionLogRepository.save(exLog);

        try {
            // Step 1: Fetch credentials
            masterLog.append(logLine("Fetching credentials from CyberArk / credential vault..."));
            ResolvedCredential credential = credentialProvider.fetchCredential(server);
            masterLog.append(logLine("Credentials resolved for user: " + credential.username()));

            // Step 2: PRE-PATCH — stop configured services
            serviceLifecycleManager.stopServices(server, credential, masterLog);

            ExecutionResult result;

            if (patch.getFilePath() != null && !patch.getFilePath().isBlank()) {
                // ── Step 3a: Manual file-based patch ──────────────────────────
                String localFilePath = resolveLocalFilePath(patch.getFilePath());
                String remoteFilePath = resolveRemoteFilePath(server, patch);
                String installCmd    = resolveFileInstallCommand(server, patch, remoteFilePath);

                masterLog.append(logLine("Patch type       : MANUAL FILE UPLOAD"));
                masterLog.append(logLine("Local file       : " + localFilePath));
                masterLog.append(logLine("Remote deploy    : " + remoteFilePath));
                masterLog.append(logLine("Install command  : " + installCmd));
                masterLog.append(logLine("───────────────────────────────────────────────────────"));

                // Step 3a: Transfer file + execute install via strategy
                masterLog.append(logLine("Starting file transfer + install via " + resolveStrategyName(server) + "..."));
                result = remoteExecutionStrategy.executeWithFile(server, localFilePath, remoteFilePath, installCmd, credential);

            } else {
                // ── Step 2b: Standard catalogue-based patch ───────────────────
                String command = resolveCommand(server, patch, credential);
                masterLog.append(logLine("Patch type       : CATALOGUE / PACKAGE MANAGER"));
                masterLog.append(logLine("Resolved command : " + command));
                masterLog.append(logLine("───────────────────────────────────────────────────────"));

                // Step 3b: Execute remotely
                masterLog.append(logLine("Starting remote execution via " + resolveStrategyName(server) + "..."));
                result = remoteExecutionStrategy.execute(server, command, credential);
            }

            masterLog.append(logLine("───────────────────────────────────────────────────────"));
            if (result.output() != null) masterLog.append(result.output());
            masterLog.append(logLine("───────────────────────────────────────────────────────"));

            if (result.success()) {
                masterLog.append(logLine("✓ Patch execution SUCCESSFUL (exit code: " + result.exitCode() + ")"));
                if (patch.isRequiresReboot()) {
                    masterLog.append(logLine("⚠  NOTE: Server reboot required to complete patching"));
                }

                // Update job
                job.setStatus(PatchStatus.COMPLETED);
                job.setCompletedAt(LocalDateTime.now());
                job.setExecutionLog(masterLog.toString());

                // Update server
                server.setLastPatchDate(LocalDateTime.now());
                server.setComplianceStatus(ComplianceStatus.COMPLIANT);
                server.setLastComplianceCheck(LocalDateTime.now());
                serverRepository.save(server);

                // Update execution log
                exLog.setStatus(PatchStatus.COMPLETED);
                exLog.setCompletedAt(LocalDateTime.now());
                exLog.setExitCode(result.exitCode());
                exLog.setLogOutput(result.output());

                // Record compliance
                complianceService.recordCompliance(server.getId(), patch.getId(),
                    ComplianceStatus.COMPLIANT, "SYSTEM");

                auditLogService.log("EXECUTE_JOB", "PatchJob", id,
                    "Completed successfully on " + server.getName(), "SYSTEM");
                log.info("Job {} completed successfully on server {}", id, server.getName());

            } else {
                handleFailure(job, exLog, server, patch, masterLog, result.error(), result.exitCode(), id);
            }

        } catch (Exception e) {
            handleFailure(job, exLog, server, patch, masterLog, e.getMessage(), 1, id);
        } finally {
            // Step 4: POST-PATCH — always restart services, even if patch failed
            try {
                ResolvedCredential restartCred = credentialProvider.fetchCredential(server);
                serviceLifecycleManager.startServices(server, restartCred, masterLog);
            } catch (Exception ex) {
                masterLog.append(logLine("[POST-PATCH] WARNING — Could not fetch credentials for service restart: " + ex.getMessage()));
                log.warn("[ServiceLifecycle] Could not restart services on {} after patch — credential fetch failed", server.getName(), ex);
            }
        }

        exLog.setLogOutput(masterLog.toString());
        executionLogRepository.save(exLog);
        patchJobRepository.save(job);
    }

    /* ─── Helpers ─────────────────────────────────────────────────────────── */

    /**
     * Resolves the actual local path of a patch file.
     *
     * <p>The stored path is an absolute path recorded at upload time. If the app has since
     * been moved to a different directory (e.g. a new project folder), the absolute path
     * will no longer exist. In that case we fall back to the configured uploadDir using
     * just the filename — allowing files to be migrated simply by copying them to the
     * current project's patch-uploads folder.
     */
    private String resolveLocalFilePath(String storedPath) {
        java.io.File stored = new java.io.File(storedPath);
        if (stored.exists()) {
            return storedPath; // happy path — stored path is still valid
        }

        // Fallback: look for the file by name in the current upload directory
        String fileName = stored.getName();
        java.io.File fallback = java.nio.file.Paths.get(uploadDir).resolve(fileName).toFile();
        if (fallback.exists()) {
            log.info("Patch file not found at stored path '{}'; using fallback '{}'",
                     storedPath, fallback.getAbsolutePath());
            return fallback.getAbsolutePath();
        }

        // Neither found — return original so the caller can surface a clear error
        log.warn("Patch file '{}' not found at stored path or fallback uploadDir '{}'",
                 fileName, uploadDir);
        return storedPath;
    }

    private void handleFailure(PatchJob job, ExecutionLog exLog, Server server, Patch patch,
                               StringBuilder log, String error, int exitCode, Long id) {
        log.append(logLine("✗ Patch execution FAILED — exit code: " + exitCode));
        if (error != null) log.append(logLine("Error: " + error));

        job.setStatus(PatchStatus.FAILED);
        job.setCompletedAt(LocalDateTime.now());
        job.setErrorMessage(error);
        job.setExecutionLog(log.toString());

        server.setComplianceStatus(ComplianceStatus.NON_COMPLIANT);
        serverRepository.save(server);

        exLog.setStatus(PatchStatus.FAILED);
        exLog.setCompletedAt(LocalDateTime.now());
        exLog.setExitCode(exitCode);
        exLog.setErrorMessage(error);

        try {
            complianceService.recordCompliance(server.getId(), patch.getId(),
                ComplianceStatus.NON_COMPLIANT, "SYSTEM");
        } catch (Exception ignored) {}

        auditLogService.log("EXECUTE_JOB_FAILED", "PatchJob", id,
            "FAILED on " + server.getName() + ": " + error, "SYSTEM");
        this.log.error("Job {} failed on server {}: {}", id, server.getName(), error);
    }

    /**
     * Builds the full remote file path: deployPath directory + original filename.
     * Falls back to OS-appropriate defaults if deployPath is not set.
     */
    private String resolveRemoteFilePath(Server server, Patch patch) {
        // Derive the original filename from the local stored path
        String localPath = patch.getFilePath();
        String fileName  = java.nio.file.Paths.get(localPath).getFileName().toString();
        // Strip the timestamp prefix we added at upload time (e.g. "1712345678_KB5031356.msu")
        if (fileName.matches("\\d+_.*")) {
            fileName = fileName.substring(fileName.indexOf('_') + 1);
        }

        String deployDir = patch.getDeployPath();
        if (deployDir == null || deployDir.isBlank()) {
            deployDir = server.getOsType() == com.patchmgmt.enums.OsType.WINDOWS
                ? "C:\\Patches\\" : "/tmp/patches/";
        }

        return switch (server.getOsType()) {
            case WINDOWS -> {
                String sep = deployDir.endsWith("\\") || deployDir.endsWith("/") ? "" : "\\";
                yield deployDir + sep + fileName;
            }
            case LINUX -> {
                String sep = deployDir.endsWith("/") ? "" : "/";
                yield deployDir + sep + fileName;
            }
        };
    }

    /**
     * Builds the install command for a manually uploaded patch file.
     *
     * <p>Priority:
     * <ol>
     *   <li>Use the patch's {@code installCommand} verbatim if set (supports {@code {FILE}} placeholder).
     *   <li>Auto-detect from file extension (.msu, .exe, .deb, .rpm).
     *   <li>Fall back to a sensible default per OS.
     * </ol>
     */
    private String resolveFileInstallCommand(Server server, Patch patch, String remoteFilePath) {
        // Derive just the basename from the remote path (e.g. "nano_install.sh")
        String remoteFileName = remoteFilePath.contains("/")
            ? remoteFilePath.substring(remoteFilePath.lastIndexOf('/') + 1)
            : remoteFilePath.substring(remoteFilePath.lastIndexOf('\\') + 1);

        String custom = patch.getInstallCommand();
        if (custom != null && !custom.isBlank()) {
            // 1. Replace {FILE} → full remote path  (e.g. /tmp/patches/nano_install.sh)
            // 2. Replace {FILENAME} → just the basename (e.g. nano_install.sh)
            String resolved = custom
                .replace("{FILE}", remoteFilePath)
                .replace("{FILENAME}", remoteFileName);

            // 3. If the command still has no reference to the actual remote file/path,
            //    ensure it runs in the correct directory by prepending a cd.
            //    This handles cases like "chmod +x somefile.sh && ./somefile.sh" where
            //    the user typed a different (or stale) filename.
            boolean referencesFile = resolved.contains(remoteFilePath)
                || resolved.contains(remoteFileName);

            if (!referencesFile) {
                // Extract the remote directory and cd into it before running the command
                String remoteDir = remoteFilePath.contains("/")
                    ? remoteFilePath.substring(0, remoteFilePath.lastIndexOf('/'))
                    : remoteFilePath.substring(0, remoteFilePath.lastIndexOf('\\'));
                log.warn("Install command '{}' does not reference the uploaded file '{}'. " +
                         "Prefixing with 'cd {}' so it resolves relative to the upload directory.",
                         custom, remoteFileName, remoteDir);
                resolved = "cd " + remoteDir + " && " + resolved;
            }

            return resolved;
        }

        // ── Auto-generate command from file extension ──────────────────────────
        String ext = remoteFilePath.contains(".")
            ? remoteFilePath.substring(remoteFilePath.lastIndexOf('.')).toLowerCase()
            : "";

        return switch (server.getOsType()) {
            case WINDOWS -> switch (ext) {
                case ".msu"  -> "wusa.exe \"" + remoteFilePath + "\" /quiet /norestart";
                case ".exe"  -> "\"" + remoteFilePath + "\" /silent /norestart";
                case ".msp"  -> "msiexec.exe /p \"" + remoteFilePath + "\" /quiet /norestart";
                case ".msi"  -> "msiexec.exe /i \"" + remoteFilePath + "\" /quiet /norestart";
                default      -> "wusa.exe \"" + remoteFilePath + "\" /quiet /norestart";
            };
            case LINUX -> switch (ext) {
                case ".deb"  -> "DEBIAN_FRONTEND=noninteractive dpkg -i " + remoteFilePath;
                case ".rpm"  -> "rpm -Uvh " + remoteFilePath;
                case ".sh"   -> "chmod +x " + remoteFilePath + " && bash " + remoteFilePath;
                case ".tar", ".gz", ".tgz" ->
                    "tar -xzf " + remoteFilePath + " -C /tmp && echo 'Archive extracted — run post-install manually'";
                default      -> "bash " + remoteFilePath;
            };
        };
    }

    private String resolveCommand(Server server, Patch patch, ResolvedCredential credential) {
        String installCmd = patch.getInstallCommand();

        // Detect if the stored installCommand is Windows-specific
        boolean isWindowsCmd = installCmd != null && !installCmd.isBlank() &&
            (installCmd.toLowerCase().contains("wusa") ||
             installCmd.toLowerCase().contains(".exe") ||
             installCmd.toLowerCase().contains(".msu") ||
             installCmd.toLowerCase().contains("powershell"));

        // Detect if the stored installCommand is Linux-specific
        boolean isLinuxCmd = installCmd != null && !installCmd.isBlank() &&
            (installCmd.contains("apt") || installCmd.contains("yum") ||
             installCmd.contains("dnf") || installCmd.contains("rpm"));

        return switch (server.getOsType()) {
            case LINUX -> {
                // If stored command is a Windows command, ignore it and use apt-get
                String baseCmd = isLinuxCmd ? installCmd : "apt-get update -y && apt-get upgrade -y";
                // For non-interactive SSH: pipe the password into sudo -S so no TTY is needed.
                // DEBIAN_FRONTEND=noninteractive suppresses apt prompts.
                String password = credential.passwordAsString();
                yield "echo '" + password.replace("'", "'\\''")
                    + "' | sudo -S sh -c 'DEBIAN_FRONTEND=noninteractive " + baseCmd + "'";
            }
            case WINDOWS -> {
                // If stored command is a Linux command, ignore it and use wusa
                if (isWindowsCmd) yield installCmd;
                yield "wusa.exe /install " + patch.getPatchId() + ".msu /quiet /norestart";
            }
        };
    }

    private String resolveStrategyName(Server server) {
        return switch (server.getOsType()) {
            case WINDOWS -> "WinRM/PowerShell";
            case LINUX   -> "SSH";
        };
    }

    private String logLine(String msg) {
        return "[" + LocalDateTime.now() + "] " + msg + "\n";
    }

    /* ─── Queries ─────────────────────────────────────────────────────────── */
    @Override @Transactional(readOnly = true)
    public Optional<PatchJob> findById(Long id)             { return patchJobRepository.findById(id); }

    @Override @Transactional(readOnly = true)
    public List<PatchJob> findAll()                          { return patchJobRepository.findAllOrderedByCreatedAtDesc(); }

    @Override @Transactional(readOnly = true)
    public List<PatchJob> findByStatus(PatchStatus status)  { return patchJobRepository.findByStatus(status); }

    @Override @Transactional(readOnly = true)
    public List<PatchJob> findByServerId(Long serverId)      { return patchJobRepository.findByServer_Id(serverId); }

    @Override @Transactional(readOnly = true)
    public List<PatchJob> findByUser(String username) {
        return userRepository.findByUsername(username)
            .map(u -> patchJobRepository.findByCreatedBy_Id(u.getId()))
            .orElse(List.of());
    }

    @Override @Transactional(readOnly = true)
    public List<PatchJob> findRecent()                       { return patchJobRepository.findTop10ByOrderByCreatedAtDesc(); }
}
