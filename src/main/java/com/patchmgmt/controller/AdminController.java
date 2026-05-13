package com.patchmgmt.controller;

import com.patchmgmt.dto.UserRegistrationDto;
import com.patchmgmt.enums.PatchStatus;
import com.patchmgmt.service.AuditLogService;
import com.patchmgmt.service.PatchJobService;
import com.patchmgmt.service.UserService;
import com.patchmgmt.service.impl.DeletionRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.patchmgmt.enums.UserRole;
import com.patchmgmt.service.ServerService;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final AuditLogService auditLogService;
    private final DeletionRequestService deletionRequestService;
    private final PatchJobService patchJobService;
    private final ServerService serverService;

    @GetMapping
    public String adminHome(Model model) {
        model.addAttribute("users", userService.findAll());
        return "admin/users";
    }

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userService.findAll());
        return "admin/users";
    }

    @GetMapping("/users/new")
    public String newUserForm(Model model) {
        model.addAttribute("user", new UserRegistrationDto());
        model.addAttribute("roles", UserRole.values());
        return "admin/user-form";
    }

    @PostMapping("/users/new")
    public String createUser(@Valid @ModelAttribute("user") UserRegistrationDto dto,
                             BindingResult result, Model model,
                             Authentication auth, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("roles", UserRole.values());
            return "admin/user-form";
        }
        try {
            userService.register(dto);
            auditLogService.log("CREATE_USER", "AppUser", null, "Created user: " + dto.getUsername(), auth.getName());
            ra.addFlashAttribute("success", "User created successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/toggle")
    public String toggleUser(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        userService.toggleEnabled(id);
        auditLogService.log("TOGGLE_USER", "AppUser", id, "Toggled by: " + auth.getName(), auth.getName());
        ra.addFlashAttribute("success", "User status updated.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        userService.delete(id);
        auditLogService.log("DELETE_USER", "AppUser", id, "Deleted by: " + auth.getName(), auth.getName());
        ra.addFlashAttribute("success", "User deleted.");
        return "redirect:/admin/users";
    }

    @GetMapping("/audit-logs")
    public String auditLogs(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("logs", auditLogService.findAll(PageRequest.of(page, 20)));
        model.addAttribute("currentPage", page);
        return "admin/audit-logs";
    }

    /* ─── Approvals Hub ──────────────────────────────────────────── */

    @GetMapping("/approvals")
    public String approvals(Model model) {
        model.addAttribute("pendingJobs", patchJobService.findByStatus(PatchStatus.PENDING));
        model.addAttribute("pendingDeletions", deletionRequestService.findPending());
        model.addAttribute("pendingServers", serverService.findPendingApproval());
        return "admin/approvals";
    }

    /** Approve a pending patch job (inline from approvals page) */
    @PostMapping("/approvals/jobs/{id}/approve")
    public String approveJob(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        patchJobService.approve(id, auth.getName());
        ra.addFlashAttribute("success", "Job #" + id + " approved.");
        return "redirect:/admin/approvals";
    }

    /** Reject (cancel) a pending patch job */
    @PostMapping("/approvals/jobs/{id}/reject")
    public String rejectJob(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        patchJobService.cancel(id);
        auditLogService.log("REJECT_JOB", "PatchJob", id, "Rejected by: " + auth.getName(), auth.getName());
        ra.addFlashAttribute("success", "Job #" + id + " rejected and cancelled.");
        return "redirect:/admin/approvals";
    }

    /** Approve a deletion request — actually deletes the entity */
    @PostMapping("/approvals/delete/{id}/approve")
    public String approveDeletion(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            deletionRequestService.approve(id, auth.getName());
            ra.addFlashAttribute("success", "Deletion approved and completed.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Deletion failed: " + e.getMessage());
        }
        return "redirect:/admin/approvals";
    }

    /** Reject a deletion request — entity survives */
    @PostMapping("/approvals/delete/{id}/reject")
    public String rejectDeletion(@PathVariable Long id,
                                  @RequestParam(defaultValue = "Rejected by admin") String reason,
                                  Authentication auth, RedirectAttributes ra) {
        deletionRequestService.reject(id, auth.getName(), reason);
        ra.addFlashAttribute("success", "Deletion request rejected.");
        return "redirect:/admin/approvals";
    }

    /** Approve a pending server creation */
    @PostMapping("/approvals/servers/{id}/approve")
    public String approveServer(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        serverService.approveServer(id, auth.getName());
        auditLogService.log("APPROVE_SERVER", "Server", id, "Creation approved by: " + auth.getName(), auth.getName());
        ra.addFlashAttribute("success", "Server creation approved.");
        return "redirect:/admin/approvals";
    }

    /** Reject a pending server creation (permanently deletes record) */
    @PostMapping("/approvals/servers/{id}/reject")
    public String rejectServer(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        serverService.delete(id);
        auditLogService.log("REJECT_SERVER", "Server", id, "Creation rejected and deleted by: " + auth.getName(), auth.getName());
        ra.addFlashAttribute("success", "Server creation rejected and record permanently deleted.");
        return "redirect:/admin/approvals";
    }
}
