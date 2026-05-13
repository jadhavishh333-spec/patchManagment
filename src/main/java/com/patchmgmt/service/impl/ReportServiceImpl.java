package com.patchmgmt.service.impl;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.opencsv.CSVWriter;
import com.patchmgmt.dto.ReportFilterDto;
import com.patchmgmt.entity.AuditLog;
import com.patchmgmt.entity.ComplianceRecord;
import com.patchmgmt.entity.PatchJob;
import com.patchmgmt.repository.AuditLogRepository;
import com.patchmgmt.repository.ComplianceRecordRepository;
import com.patchmgmt.repository.PatchJobRepository;
import com.patchmgmt.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final PatchJobRepository patchJobRepository;
    private final ComplianceRecordRepository complianceRecordRepository;
    private final AuditLogRepository auditLogRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /* ─── CSV: Patch Jobs ─────────────────────────────────────────────────── */
    @Override
    public byte[] exportPatchReportCsv(ReportFilterDto filter) {
        List<PatchJob> jobs = filterJobs(filter);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             CSVWriter writer = new CSVWriter(new OutputStreamWriter(bos, StandardCharsets.UTF_8))) {

            writer.writeNext(new String[]{"ID","Title","Server","IP","OS","Patch","Severity","Status",
                "Scheduled","Started","Completed","Retries","Created By"});

            for (PatchJob j : jobs) {
                writer.writeNext(new String[]{
                    String.valueOf(j.getId()),
                    j.getTitle(),
                    j.getServer().getName(),
                    j.getServer().getIpAddress(),
                    j.getServer().getOsType().name(),
                    j.getPatch().getPatchId(),
                    j.getPatch().getSeverity().name(),
                    j.getStatus().name(),
                    j.getScheduledAt() != null ? j.getScheduledAt().format(FMT) : "",
                    j.getStartedAt()   != null ? j.getStartedAt().format(FMT)   : "",
                    j.getCompletedAt() != null ? j.getCompletedAt().format(FMT) : "",
                    String.valueOf(j.getRetryCount()),
                    j.getCreatedBy() != null ? j.getCreatedBy().getUsername() : ""
                });
            }
            writer.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            log.error("CSV export failed: {}", e.getMessage());
            throw new RuntimeException("Failed to generate patch CSV report", e);
        }
    }

    /* ─── PDF: Compliance Report ──────────────────────────────────────────── */
    @Override
    public byte[] exportComplianceReportPdf(ReportFilterDto filter) {
        List<ComplianceRecord> records = complianceRecordRepository.findAll();
        if (filter.getEnvironment() != null && !filter.getEnvironment().isEmpty()) {
            records = records.stream()
                .filter(r -> filter.getEnvironment().equals(r.getServer().getEnvironment()))
                .collect(Collectors.toList());
        }

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(doc, bos);
            doc.open();

            // Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.WHITE);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

            Paragraph title = new Paragraph("Patch Compliance Report", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18));
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            doc.add(title);

            Paragraph subtitle = new Paragraph("Generated: " + java.time.LocalDateTime.now().format(FMT) +
                (filter.getEnvironment() != null && !filter.getEnvironment().isEmpty() ? " | Environment: " + filter.getEnvironment() : ""),
                FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY));
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(20);
            doc.add(subtitle);

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3f, 2.5f, 1.5f, 2f, 2.5f, 1.5f});

            String[] headers = {"Server", "Patch", "Status", "Environment", "Compliance Date", "Verified By"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new Color(30, 41, 59));
                cell.setPadding(7);
                table.addCell(cell);
            }

            Color rowAlt = new Color(241, 245, 249);
            int rowIdx = 0;
            for (ComplianceRecord r : records) {
                Color bg = (rowIdx++ % 2 == 0) ? Color.WHITE : rowAlt;
                addCell(table, r.getServer().getName(), cellFont, bg);
                addCell(table, r.getPatch().getPatchId(), cellFont, bg);
                addCell(table, r.getStatus().name(), cellFont, bg);
                addCell(table, r.getServer().getEnvironment() != null ? r.getServer().getEnvironment() : "-", cellFont, bg);
                addCell(table, r.getComplianceDate() != null ? r.getComplianceDate().toString() : "", cellFont, bg);
                addCell(table, r.getVerifiedBy() != null ? r.getVerifiedBy() : "", cellFont, bg);
            }

            doc.add(table);
            doc.close();
            return bos.toByteArray();
        } catch (Exception e) {
            log.error("PDF export failed: {}", e.getMessage());
            throw new RuntimeException("Failed to generate compliance PDF report", e);
        }
    }

    /* ─── CSV: Audit Log ──────────────────────────────────────────────────── */
    @Override
    public byte[] exportAuditReportCsv(ReportFilterDto filter) {
        List<AuditLog> logs = auditLogRepository.findAll();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             CSVWriter writer = new CSVWriter(new OutputStreamWriter(bos, StandardCharsets.UTF_8))) {

            writer.writeNext(new String[]{"ID","Action","Entity","Entity ID","Details","Performed By","Performed At","IP Address"});
            for (AuditLog al : logs) {
                writer.writeNext(new String[]{
                    String.valueOf(al.getId()),
                    al.getAction(),
                    al.getEntityType(),
                    al.getEntityId() != null ? al.getEntityId().toString() : "",
                    al.getDetails(),
                    al.getPerformedBy() != null ? al.getPerformedBy().getUsername() : "SYSTEM",
                    al.getPerformedAt() != null ? al.getPerformedAt().format(FMT) : "",
                    al.getIpAddress() != null ? al.getIpAddress() : ""
                });
            }
            writer.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate audit CSV report", e);
        }
    }

    /* ─── CSV: Execution Logs ─────────────────────────────────────────────── */
    @Override
    public byte[] exportExecutionLogCsv(ReportFilterDto filter) {
        List<PatchJob> jobs = filterJobs(filter);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             CSVWriter writer = new CSVWriter(new OutputStreamWriter(bos, StandardCharsets.UTF_8))) {

            writer.writeNext(new String[]{"Job ID","Job Title","Server","IP","Status","Started","Completed","Retry","Log Snippet"});
            for (PatchJob j : jobs) {
                String logSnippet = j.getExecutionLog() != null
                    ? j.getExecutionLog().substring(0, Math.min(300, j.getExecutionLog().length()))
                    : "";
                writer.writeNext(new String[]{
                    String.valueOf(j.getId()),
                    j.getTitle(),
                    j.getServer().getName(),
                    j.getServer().getIpAddress(),
                    j.getStatus().name(),
                    j.getStartedAt()   != null ? j.getStartedAt().format(FMT)   : "",
                    j.getCompletedAt() != null ? j.getCompletedAt().format(FMT) : "",
                    String.valueOf(j.getRetryCount()),
                    logSnippet
                });
            }
            writer.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate execution log CSV", e);
        }
    }

    /* ─── Helpers ─────────────────────────────────────────────────────────── */
    private List<PatchJob> filterJobs(ReportFilterDto filter) {
        List<PatchJob> jobs = patchJobRepository.findAllOrderedByCreatedAtDesc();
        if (filter.getStatus() != null) {
            jobs = jobs.stream().filter(j -> j.getStatus() == filter.getStatus()).collect(Collectors.toList());
        }
        if (filter.getEnvironment() != null && !filter.getEnvironment().isEmpty()) {
            jobs = jobs.stream()
                .filter(j -> filter.getEnvironment().equals(j.getServer().getEnvironment()))
                .collect(Collectors.toList());
        }
        return jobs;
    }

    private void addCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(6);
        table.addCell(cell);
    }
}
