package com.patchmgmt.controller;

import com.patchmgmt.dto.PatchDto;
import com.patchmgmt.entity.Patch;
import com.patchmgmt.enums.OsType;
import com.patchmgmt.enums.PatchSeverity;
import com.patchmgmt.service.AuditLogService;
import com.patchmgmt.service.PatchService;
import com.patchmgmt.service.impl.DeletionRequestService;
import com.patchmgmt.service.impl.PatchServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/patches")
@RequiredArgsConstructor
public class PatchController {

    private final PatchService patchService;
    private final PatchServiceImpl patchServiceImpl; // for savePatchFile
    private final AuditLogService auditLogService;
    private final DeletionRequestService deletionRequestService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("patches", patchService.findAll());
        return "patches/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Patch patch = patchService.findById(id)
            .orElseThrow(() -> new RuntimeException("Patch not found"));
        model.addAttribute("patch", patch);
        model.addAttribute("deletionPending",
            deletionRequestService.hasPendingRequest("PATCH", id));
        return "patches/detail";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newForm(Model model) {
        model.addAttribute("patch", new PatchDto());
        model.addAttribute("osTypes", OsType.values());
        model.addAttribute("severities", PatchSeverity.values());
        return "patches/form";
    }

    /**
     * Create patch — supports optional binary file upload.
     * Form must use enctype="multipart/form-data".
     */
    @PostMapping(value = "/new", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public String create(@Valid @ModelAttribute("patch") PatchDto dto,
                         BindingResult result,
                         @RequestParam(value = "patchFile", required = false) MultipartFile patchFile,
                         Model model,
                         Authentication auth,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("osTypes", OsType.values());
            model.addAttribute("severities", PatchSeverity.values());
            return "patches/form";
        }

        // Save uploaded binary (if provided)
        if (patchFile != null && !patchFile.isEmpty()) {
            String filePath = patchServiceImpl.savePatchFile(patchFile);
            dto.setFilePath(filePath);
        }

        Patch saved = patchService.save(dto, auth.getName());
        auditLogService.log("CREATE_PATCH", "Patch", saved.getId(),
            "Created: " + saved.getTitle() + (saved.getFilePath() != null ? " [with binary]" : ""),
            auth.getName());
        ra.addFlashAttribute("success", "Patch created successfully!");
        return "redirect:/patches";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editForm(@PathVariable Long id, Model model) {
        Patch patch = patchService.findById(id)
            .orElseThrow(() -> new RuntimeException("Patch not found"));
        PatchDto dto = new PatchDto();
        dto.setId(patch.getId());           dto.setTitle(patch.getTitle());
        dto.setPatchId(patch.getPatchId()); dto.setDescription(patch.getDescription());
        dto.setOsType(patch.getOsType());   dto.setSeverity(patch.getSeverity());
        dto.setReleaseDate(patch.getReleaseDate()); dto.setRequiresReboot(patch.isRequiresReboot());
        dto.setInstallCommand(patch.getInstallCommand());
        dto.setFilePath(patch.getFilePath());
        dto.setDeployPath(patch.getDeployPath());
        model.addAttribute("patch", dto);
        model.addAttribute("osTypes", OsType.values());
        model.addAttribute("severities", PatchSeverity.values());
        return "patches/form";
    }

    /**
     * Update patch — supports replacing the binary file.
     * If no new file is uploaded the existing stored file is kept.
     */
    @PostMapping(value = "/{id}/edit", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("patch") PatchDto dto,
                         BindingResult result,
                         @RequestParam(value = "patchFile", required = false) MultipartFile patchFile,
                         Model model,
                         Authentication auth,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("osTypes", OsType.values());
            model.addAttribute("severities", PatchSeverity.values());
            return "patches/form";
        }

        // Overwrite stored binary only when a new file is uploaded
        if (patchFile != null && !patchFile.isEmpty()) {
            String filePath = patchServiceImpl.savePatchFile(patchFile);
            dto.setFilePath(filePath);
        }

        patchService.update(id, dto);
        auditLogService.log("UPDATE_PATCH", "Patch", id,
            "Updated by: " + auth.getName() + (patchFile != null && !patchFile.isEmpty() ? " [binary replaced]" : ""),
            auth.getName());
        ra.addFlashAttribute("success", "Patch updated!");
        return "redirect:/patches";
    }

    @PostMapping("/{id}/request-delete")
    public String requestDelete(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        Patch patch = patchService.findById(id)
            .orElseThrow(() -> new RuntimeException("Patch not found"));
        try {
            deletionRequestService.request("PATCH", id, patch.getTitle(), auth.getName());
            ra.addFlashAttribute("success",
                "Deletion request submitted for '" + patch.getTitle() + "'. Awaiting admin approval.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/patches/" + id;
    }
}

