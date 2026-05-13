package com.patchmgmt.controller;

import com.patchmgmt.dto.ServerDto;
import com.patchmgmt.entity.Server;
import com.patchmgmt.enums.ExecutionStrategyType;
import com.patchmgmt.enums.OsType;
import com.patchmgmt.exception.ResourceNotFoundException;
import com.patchmgmt.service.AuditLogService;
import com.patchmgmt.service.ServerService;
import com.patchmgmt.service.impl.DeletionRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/servers")
@RequiredArgsConstructor
public class ServerController {

    private final ServerService serverService;
    private final AuditLogService auditLogService;
    private final DeletionRequestService deletionRequestService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("servers", serverService.findAll());
        return "servers/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Server server = serverService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Server", id));
        model.addAttribute("server", server);
        model.addAttribute("auditLogs", auditLogService.findByEntity("Server", id));
        model.addAttribute("deletionPending",
            deletionRequestService.hasPendingRequest("SERVER", id));
        return "servers/detail";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("server", new ServerDto());
        model.addAttribute("osTypes", OsType.values());
        model.addAttribute("strategies", ExecutionStrategyType.values());
        return "servers/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("server") ServerDto dto,
                         BindingResult result, Model model,
                         Authentication auth, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("osTypes", OsType.values());
            model.addAttribute("strategies", ExecutionStrategyType.values());
            return "servers/form";
        }
        Server saved;
        try {
            saved = serverService.save(dto, auth.getName());
        } catch (IllegalArgumentException ex) {
            result.rejectValue("ipAddress", "duplicate.ip", ex.getMessage());
            model.addAttribute("osTypes", OsType.values());
            model.addAttribute("strategies", ExecutionStrategyType.values());
            return "servers/form";
        }
        auditLogService.log("CREATE_SERVER", "Server", saved.getId(), "Created: " + saved.getName(), auth.getName());

        if (saved.getApprovalStatus() == com.patchmgmt.enums.ApprovalStatus.PENDING) {
            ra.addFlashAttribute("success", "Server added successfully! It is currently awaiting admin approval.");
        } else {
            ra.addFlashAttribute("success", "Server added successfully and is active.");
        }

        return "redirect:/servers";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editForm(@PathVariable Long id, Model model) {
        Server server = serverService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Server", id));
        ServerDto dto = new ServerDto();
        dto.setId(server.getId()); dto.setName(server.getName());
        dto.setIpAddress(server.getIpAddress()); dto.setOsType(server.getOsType());
        dto.setOsVersion(server.getOsVersion()); dto.setEnvironment(server.getEnvironment());
        dto.setDescription(server.getDescription()); dto.setActive(server.isActive());
        dto.setBusinessUnit(server.getBusinessUnit());
        dto.setWinrmPort(server.getWinrmPort());
        dto.setSshPort(server.getSshPort());
        dto.setExecutionStrategy(server.getExecutionStrategy());
        dto.setCyberArkSafe(server.getCyberArkSafe());
        dto.setCyberArkObject(server.getCyberArkObject());
        dto.setTags(server.getTags());
        // Service lifecycle fields — must be copied so they survive re-edit
        dto.setPreStopServices(server.getPreStopServices());
        dto.setIisStopMode(server.getIisStopMode() != null
                ? server.getIisStopMode()
                : com.patchmgmt.enums.IisStopMode.APPPOOL);
        model.addAttribute("server", dto);
        model.addAttribute("osTypes", OsType.values());
        model.addAttribute("strategies", ExecutionStrategyType.values());
        return "servers/form";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("server") ServerDto dto,
                         BindingResult result, Model model,
                         Authentication auth, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("osTypes", OsType.values());
            model.addAttribute("strategies", ExecutionStrategyType.values());
            return "servers/form";
        }
        try {
            serverService.update(id, dto);
        } catch (IllegalArgumentException ex) {
            result.rejectValue("ipAddress", "duplicate.ip", ex.getMessage());
            model.addAttribute("osTypes", OsType.values());
            model.addAttribute("strategies", ExecutionStrategyType.values());
            return "servers/form";
        }
        auditLogService.log("UPDATE_SERVER", "Server", id, "Updated by: " + auth.getName(), auth.getName());
        ra.addFlashAttribute("success", "Server updated successfully!");
        return "redirect:/servers";
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public String toggle(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        serverService.toggleActive(id);
        ra.addFlashAttribute("success", "Server status updated.");
        return "redirect:/servers";
    }

    @PostMapping("/{id}/request-delete")
    public String requestDelete(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        Server server = serverService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Server", id));
        try {
            deletionRequestService.request("SERVER", id, server.getName(), auth.getName());
            ra.addFlashAttribute("success",
                "Deletion request submitted for '" + server.getName() + "'. Awaiting admin approval.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/servers/" + id;
    }
}
