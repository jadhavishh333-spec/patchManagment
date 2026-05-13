package com.patchmgmt.controller;

import com.patchmgmt.service.ComplianceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceService complianceService;

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("compliance", complianceService.getComplianceDashboard());
        model.addAttribute("environments", complianceService.getEnvironments());
        return "compliance/dashboard";
    }

    @GetMapping("/server/{id}")
    public String serverCompliance(@PathVariable Long id, Model model) {
        model.addAttribute("records", complianceService.getByServer(id));
        model.addAttribute("serverId", id);
        return "compliance/server";
    }

    @GetMapping("/environment/{env}")
    public String envCompliance(@PathVariable String env, Model model) {
        model.addAttribute("records", complianceService.getByEnvironment(env));
        model.addAttribute("environment", env);
        model.addAttribute("compliancePct", complianceService.getCompliancePercentage(env));
        return "compliance/environment";
    }
}
