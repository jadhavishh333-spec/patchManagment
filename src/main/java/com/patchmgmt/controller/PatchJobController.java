package com.patchmgmt.controller;

import com.patchmgmt.dto.PatchJobDto;
import com.patchmgmt.entity.ExecutionLog;
import com.patchmgmt.entity.PatchJob;
import com.patchmgmt.enums.PatchStatus;
import com.patchmgmt.repository.ExecutionLogRepository;
import com.patchmgmt.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class PatchJobController {

    private final PatchJobService patchJobService;
    private final ServerService serverService;
    private final PatchService patchService;
    private final AuditLogService auditLogService;
    private final ExecutionLogRepository executionLogRepository;

    @GetMapping
    public String list(@RequestParam(required = false) String status,
                       Model model, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        List<PatchJob> jobs;
        if (status != null && !status.isBlank()) {
            try {
                PatchStatus filterStatus = PatchStatus.valueOf(status.toUpperCase());
                // Admins see all jobs with that status; users see only their own
                List<PatchJob> allWithStatus = patchJobService.findByStatus(filterStatus);
                jobs = isAdmin
                    ? allWithStatus
                    : allWithStatus.stream()
                        .filter(j -> j.getCreatedBy() != null
                                     && j.getCreatedBy().getUsername().equals(auth.getName()))
                        .toList();
            } catch (IllegalArgumentException e) {
                // Unknown status value — fall back to showing everything
                jobs = isAdmin ? patchJobService.findAll() : patchJobService.findByUser(auth.getName());
            }
        } else {
            jobs = isAdmin ? patchJobService.findAll() : patchJobService.findByUser(auth.getName());
        }

        model.addAttribute("jobs", jobs);
        model.addAttribute("statuses", PatchStatus.values());
        model.addAttribute("activeStatus", status);   // so the template can highlight the active chip
        return "jobs/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        PatchJob job = patchJobService.findById(id)
            .orElseThrow(() -> new com.patchmgmt.exception.ResourceNotFoundException("PatchJob", id));
        List<ExecutionLog> execLogs = executionLogRepository.findByPatchJobIdOrderByCreatedAtDesc(id);
        model.addAttribute("job", job);
        model.addAttribute("execLogs", execLogs);
        return "jobs/detail";
    }

    @GetMapping("/{id}/logs")
    public String logs(@PathVariable Long id, Model model) {
        PatchJob job = patchJobService.findById(id)
            .orElseThrow(() -> new com.patchmgmt.exception.ResourceNotFoundException("PatchJob", id));
        List<ExecutionLog> execLogs = executionLogRepository.findByPatchJobIdOrderByCreatedAtDesc(id);
        model.addAttribute("job", job);
        model.addAttribute("execLogs", execLogs);
        return "jobs/logs";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("job", new PatchJobDto());
        model.addAttribute("servers", serverService.findActive());
        model.addAttribute("patches", patchService.findAll());
        return "jobs/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("job") PatchJobDto dto,
                         BindingResult result, Model model,
                         Authentication auth, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("servers", serverService.findActive());
            model.addAttribute("patches", patchService.findAll());
            return "jobs/form";
        }
        PatchJob saved = patchJobService.create(dto, auth.getName());
        ra.addFlashAttribute("success", "Patch job '" + saved.getTitle() + "' created! Waiting for admin approval.");
        return "redirect:/jobs/" + saved.getId();
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public String approve(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        patchJobService.approve(id, auth.getName());
        ra.addFlashAttribute("success", "Job approved! A user can now execute it.");
        return "redirect:/jobs/" + id;
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public String execute(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        PatchJob job = patchJobService.findById(id)
            .orElseThrow(() -> new com.patchmgmt.exception.ResourceNotFoundException("PatchJob", id));
        if (job.getStatus() == PatchStatus.PENDING) {
            ra.addFlashAttribute("error", "Job must be approved by an admin before execution.");
            return "redirect:/jobs/" + id;
        }
        if (job.getStatus() != PatchStatus.APPROVED) {
            ra.addFlashAttribute("error", "Job is not in APPROVED state (current: " + job.getStatus() + ").");
            return "redirect:/jobs/" + id;
        }
        patchJobService.execute(id);
        ra.addFlashAttribute("success", "Execution started by " + auth.getName() + "! Logs are streaming below…");
        return "redirect:/jobs/" + id;
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public String retry(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        patchJobService.retryJob(id, auth.getName());
        // Call execute through the Spring proxy so @Async works correctly
        patchJobService.execute(id);
        ra.addFlashAttribute("success", "Retry initiated by " + auth.getName() + " for job #" + id + ".");
        return "redirect:/jobs/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        patchJobService.cancel(id);
        auditLogService.log("CANCEL_JOB", "PatchJob", id, "Cancelled by: " + auth.getName(), auth.getName());
        ra.addFlashAttribute("success", "Job cancelled.");
        return "redirect:/jobs";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            patchJobService.delete(id);
            auditLogService.log("DELETE_JOB", "PatchJob", id, "Deleted by: " + auth.getName(), auth.getName());
            ra.addFlashAttribute("success", "Patch job #" + id + " has been permanently deleted.");
        } catch (com.patchmgmt.exception.ValidationException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/jobs/" + id;
        }
        return "redirect:/jobs";
    }
}
