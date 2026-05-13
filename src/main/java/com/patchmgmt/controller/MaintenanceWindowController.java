package com.patchmgmt.controller;

import com.patchmgmt.dto.MaintenanceWindowDto;
import com.patchmgmt.entity.MaintenanceWindow;
import com.patchmgmt.enums.WindowType;
import com.patchmgmt.service.MaintenanceWindowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;

@Controller
@RequestMapping("/maintenance")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class MaintenanceWindowController {

    private final MaintenanceWindowService maintenanceWindowService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("windows", maintenanceWindowService.findAll());
        return "maintenance/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("window", new MaintenanceWindowDto());
        model.addAttribute("windowTypes", WindowType.values());
        model.addAttribute("daysOfWeek", DayOfWeek.values());
        return "maintenance/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("window") MaintenanceWindowDto dto,
                         BindingResult result, Model model,
                         Authentication auth, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("windowTypes", WindowType.values());
            model.addAttribute("daysOfWeek", DayOfWeek.values());
            return "maintenance/form";
        }
        maintenanceWindowService.create(dto, auth.getName());
        ra.addFlashAttribute("success", "Maintenance window '" + dto.getName() + "' created.");
        return "redirect:/maintenance";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        MaintenanceWindow mw = maintenanceWindowService.findById(id)
            .orElseThrow(() -> new com.patchmgmt.exception.ResourceNotFoundException("MaintenanceWindow", id));
        MaintenanceWindowDto dto = MaintenanceWindowDto.builder()
            .id(mw.getId()).name(mw.getName()).description(mw.getDescription())
            .windowType(mw.getWindowType()).startTime(mw.getStartTime()).endTime(mw.getEndTime())
            .dayOfWeek(mw.getDayOfWeek()).startDate(mw.getStartDate()).endDate(mw.getEndDate())
            .environment(mw.getEnvironment()).active(mw.isActive()).build();
        model.addAttribute("window", dto);
        model.addAttribute("windowTypes", WindowType.values());
        model.addAttribute("daysOfWeek", DayOfWeek.values());
        return "maintenance/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("window") MaintenanceWindowDto dto,
                         BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("windowTypes", WindowType.values());
            model.addAttribute("daysOfWeek", DayOfWeek.values());
            return "maintenance/form";
        }
        maintenanceWindowService.update(id, dto);
        ra.addFlashAttribute("success", "Maintenance window updated.");
        return "redirect:/maintenance";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        maintenanceWindowService.delete(id);
        ra.addFlashAttribute("success", "Maintenance window deleted.");
        return "redirect:/maintenance";
    }
}
