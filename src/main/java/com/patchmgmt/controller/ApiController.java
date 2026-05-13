package com.patchmgmt.controller;

import com.patchmgmt.entity.ExecutionLog;
import com.patchmgmt.entity.PatchJob;
import com.patchmgmt.repository.ExecutionLogRepository;
import com.patchmgmt.service.PatchJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ApiController {

    private final PatchJobService patchJobService;
    private final ExecutionLogRepository executionLogRepository;

    /** Polling endpoint for real-time job status updates */
    @GetMapping("/jobs/{id}/status")
    public ResponseEntity<Map<String, Object>> jobStatus(@PathVariable Long id) {
        return patchJobService.findById(id)
            .map(job -> {
                Map<String, Object> res = new HashMap<>();
                res.put("id", job.getId());
                res.put("status", job.getStatus().name());
                res.put("startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null);
                res.put("completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : null);
                res.put("retryCount", job.getRetryCount());
                res.put("hasLog", job.getExecutionLog() != null && !job.getExecutionLog().isEmpty());
                return ResponseEntity.ok(res);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /** Fetch execution log for a job (for AJAX log viewer) */
    @GetMapping("/jobs/{id}/logs")
    public ResponseEntity<Map<String, Object>> jobLogs(@PathVariable Long id) {
        PatchJob job = patchJobService.findById(id).orElse(null);
        if (job == null) return ResponseEntity.notFound().build();

        List<ExecutionLog> execLogs = executionLogRepository.findByPatchJobIdOrderByCreatedAtDesc(id);
        Map<String, Object> res = new HashMap<>();
        res.put("status", job.getStatus().name());
        res.put("masterLog", job.getExecutionLog());
        res.put("executionLogCount", execLogs.size());
        return ResponseEntity.ok(res);
    }

    /** Health-check ping */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "PatchOrchestrationPlatform"));
    }

    /**
     * Suggests an install command and default deploy path based on the uploaded
     * filename extension and target OS type.
     *
     * Called by the patch form JS whenever a file is selected or the OS type changes.
     *
     * @param osType   "LINUX" or "WINDOWS"
     * @param filename original filename of the uploaded file (e.g. "axel.sh")
     */
    @GetMapping("/patches/suggest-command")
    public ResponseEntity<Map<String, String>> suggestCommand(
            @RequestParam String osType,
            @RequestParam String filename) {

        String lc  = filename.toLowerCase();
        String ext = lc.contains(".") ? lc.substring(lc.lastIndexOf('.')) : "";

        String deployPath;
        String command;

        if ("LINUX".equalsIgnoreCase(osType)) {
            deployPath = "/tmp/patches/";
            command = switch (ext) {
                case ".deb"                -> "DEBIAN_FRONTEND=noninteractive dpkg -i {FILE}";
                case ".rpm"                -> "rpm -Uvh {FILE}";
                case ".sh"                 -> "chmod +x {FILE} && bash {FILE}";
                case ".tar", ".gz", ".tgz" -> "tar -xzf {FILE} -C /tmp/patches/";
                default                    -> "bash {FILE}";
            };
        } else { // WINDOWS
            deployPath = "C:\\Patches\\";
            command = switch (ext) {
                case ".msu" -> "wusa.exe \"{FILE}\" /quiet /norestart";
                case ".exe" -> "\"{FILE}\" /silent /norestart";
                case ".msi" -> "msiexec.exe /i \"{FILE}\" /quiet /norestart";
                case ".msp" -> "msiexec.exe /p \"{FILE}\" /quiet /norestart";
                default     -> "wusa.exe \"{FILE}\" /quiet /norestart";
            };
        }

        return ResponseEntity.ok(Map.of(
            "command",    command,
            "deployPath", deployPath,
            "hint",       "Use {FILE} — it is replaced with the full remote path at execution time."
        ));
    }
}
