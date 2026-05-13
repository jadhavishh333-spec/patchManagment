package com.patchmgmt.controller;

import com.patchmgmt.config.IntegrationProperties;
import com.patchmgmt.dto.CredentialMappingDto;
import com.patchmgmt.entity.CredentialMapping;
import com.patchmgmt.service.CredentialMappingService;
import com.patchmgmt.service.ServerService;
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
@RequestMapping("/admin/credentials")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CredentialMappingController {

    private final CredentialMappingService credentialMappingService;
    private final ServerService serverService;
    private final IntegrationProperties integrationProperties;

    /** True when demo-mode=false AND cyberark disabled → LocalCredentialProvider is used */
    private boolean isLocalMode() {
        return !integrationProperties.isDemoMode() && !integrationProperties.getCyberark().isEnabled();
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("mappings", credentialMappingService.findAll());
        model.addAttribute("localMode", isLocalMode());
        return "admin/credentials";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("mapping", new CredentialMappingDto());
        model.addAttribute("servers", serverService.findAll());
        model.addAttribute("localMode", isLocalMode());
        return "admin/credential-form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("mapping") CredentialMappingDto dto,
                         BindingResult result, Model model,
                         Authentication auth, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("servers", serverService.findAll());
            model.addAttribute("localMode", isLocalMode());
            return "admin/credential-form";
        }
        try {
            credentialMappingService.create(dto, auth.getName());
            ra.addFlashAttribute("success", "Credential mapping created for server ID: " + dto.getServerId());
        } catch (com.patchmgmt.exception.ValidationException ex) {
            model.addAttribute("servers", serverService.findAll());
            model.addAttribute("localMode", isLocalMode());
            model.addAttribute("error", ex.getMessage());
            return "admin/credential-form";
        }
        return "redirect:/admin/credentials";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        CredentialMapping mapping = credentialMappingService.findById(id)
            .orElseThrow(() -> new com.patchmgmt.exception.ResourceNotFoundException("CredentialMapping", id));

        boolean localMode = isLocalMode();
        CredentialMappingDto dto = CredentialMappingDto.builder()
            .id(mapping.getId())
            .serverId(mapping.getServer().getId())
            .serverName(mapping.getServer().getName())
            .serverIp(mapping.getServer().getIpAddress())
            // In local mode the cyberark_safe column stores SSH username
            .sshUsername(localMode ? mapping.getCyberArkSafe() : null)
            // sshPassword intentionally left blank — user must re-enter to change
            .cyberArkSafe(localMode ? null : mapping.getCyberArkSafe())
            .cyberArkObject(localMode ? null : mapping.getCyberArkObject())
            .cyberArkUsername(mapping.getCyberArkUsername())
            .environment(mapping.getEnvironment())
            .notes(mapping.getNotes())
            .build();
        model.addAttribute("mapping", dto);
        model.addAttribute("servers", serverService.findAll());
        model.addAttribute("localMode", localMode);
        return "admin/credential-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("mapping") CredentialMappingDto dto,
                         BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("servers", serverService.findAll());
            model.addAttribute("localMode", isLocalMode());
            return "admin/credential-form";
        }
        try {
            credentialMappingService.update(id, dto);
            ra.addFlashAttribute("success", "Credential mapping updated.");
        } catch (com.patchmgmt.exception.ValidationException ex) {
            model.addAttribute("servers", serverService.findAll());
            model.addAttribute("localMode", isLocalMode());
            model.addAttribute("error", ex.getMessage());
            return "admin/credential-form";
        }
        return "redirect:/admin/credentials";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        credentialMappingService.delete(id);
        ra.addFlashAttribute("success", "Credential mapping deleted.");
        return "redirect:/admin/credentials";
    }
}
