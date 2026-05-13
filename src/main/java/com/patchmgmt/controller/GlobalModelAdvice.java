package com.patchmgmt.controller;

import com.patchmgmt.config.IntegrationProperties;
import com.patchmgmt.enums.PatchStatus;
import com.patchmgmt.service.PatchJobService;
import com.patchmgmt.service.impl.DeletionRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final IntegrationProperties integrationProperties;
    private final PatchJobService patchJobService;
    private final DeletionRequestService deletionRequestService;

    @ModelAttribute("requestURI")
    public String requestURI(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ModelAttribute("demoMode")
    public boolean demoMode() {
        return integrationProperties.isDemoMode();
    }

    @ModelAttribute("appVersion")
    public String appVersion() {
        return "2.0.0";
    }

    /**
     * Total pending approvals count — shown as a badge in the sidebar for admins.
     * Pending patch jobs + pending deletion requests.
     */
    @ModelAttribute("pendingApprovalCount")
    public long pendingApprovalCount(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return 0;
        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) return 0;
        long jobs = patchJobService.findByStatus(PatchStatus.PENDING).size();
        long deletions = deletionRequestService.countPending();
        return jobs + deletions;
    }
}
