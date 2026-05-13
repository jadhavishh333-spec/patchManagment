package com.patchmgmt.config;

import com.patchmgmt.entity.PatchJob;
import com.patchmgmt.enums.PatchStatus;
import com.patchmgmt.repository.PatchJobRepository;
import com.patchmgmt.service.PatchJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "patch.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class PatchJobScheduler {

    private final PatchJobRepository patchJobRepository;
    private final PatchJobService patchJobService;

    /**
     * Every minute: find all APPROVED jobs whose scheduledAt <= now, and execute them.
     * Cron: 0 * * * * * = every minute at second 0
     */
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Kolkata")
    public void executeDueJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<PatchJob> dueJobs = patchJobRepository.findDueJobs(PatchStatus.APPROVED, now);
        if (!dueJobs.isEmpty()) {
            log.info("Scheduler: found {} due job(s) to execute", dueJobs.size());
        }
        for (PatchJob job : dueJobs) {
            log.info("Auto-executing scheduled job #{}: {}", job.getId(), job.getTitle());
            patchJobService.execute(job.getId());
        }
    }
}
