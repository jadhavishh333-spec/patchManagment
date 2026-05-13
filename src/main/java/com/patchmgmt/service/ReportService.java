package com.patchmgmt.service;

import com.patchmgmt.dto.ReportFilterDto;

public interface ReportService {
    byte[] exportPatchReportCsv(ReportFilterDto filter);
    byte[] exportComplianceReportPdf(ReportFilterDto filter);
    byte[] exportAuditReportCsv(ReportFilterDto filter);
    byte[] exportExecutionLogCsv(ReportFilterDto filter);
}
