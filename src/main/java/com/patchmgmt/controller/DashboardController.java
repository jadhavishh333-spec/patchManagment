package com.patchmgmt.controller;
import com.patchmgmt.service.DashboardService;
import com.patchmgmt.service.PatchJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
@Controller @RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;
    private final PatchJobService patchJobService;
    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model, Authentication auth) {
        model.addAttribute("stats", dashboardService.getStats());
        model.addAttribute("recentJobs", patchJobService.findRecent());
        model.addAttribute("username", auth.getName());
        return "dashboard/index";
    }
}
