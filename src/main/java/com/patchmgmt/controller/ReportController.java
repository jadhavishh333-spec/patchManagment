package com.patchmgmt.controller;

import com.patchmgmt.dto.ReportFilterDto;
import com.patchmgmt.enums.PatchStatus;
import com.patchmgmt.repository.ServerRepository;
import com.patchmgmt.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ReportService reportService;
    private final ServerRepository serverRepository;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("filter", new ReportFilterDto());
        model.addAttribute("statuses", PatchStatus.values());
        model.addAttribute("environments", serverRepository.findDistinctEnvironments());
        return "reports/index";
    }

    @GetMapping("/export/patch-csv")
    public ResponseEntity<byte[]> exportPatchCsv(ReportFilterDto filter) {
        byte[] data = reportService.exportPatchReportCsv(filter);
        String filename = "patch-report-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(data);
    }

    @GetMapping("/export/compliance-pdf")
    public ResponseEntity<byte[]> exportCompliancePdf(ReportFilterDto filter) {
        byte[] data = reportService.exportComplianceReportPdf(filter);
        String filename = "compliance-report-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".pdf";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(data);
    }

    @GetMapping("/export/audit-csv")
    public ResponseEntity<byte[]> exportAuditCsv(ReportFilterDto filter) {
        byte[] data = reportService.exportAuditReportCsv(filter);
        String filename = "audit-log-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(data);
    }

    @GetMapping("/export/execution-csv")
    public ResponseEntity<byte[]> exportExecutionCsv(ReportFilterDto filter) {
        byte[] data = reportService.exportExecutionLogCsv(filter);
        String filename = "execution-log-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(data);
    }
}
