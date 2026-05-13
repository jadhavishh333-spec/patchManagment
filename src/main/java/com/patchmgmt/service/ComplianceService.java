package com.patchmgmt.service;

import com.patchmgmt.dto.ComplianceDashboardDto;
import com.patchmgmt.dto.ComplianceRecordDto;
import com.patchmgmt.enums.ComplianceStatus;

import java.util.List;

public interface ComplianceService {
    ComplianceDashboardDto getComplianceDashboard();
    List<ComplianceRecordDto> getByServer(Long serverId);
    List<ComplianceRecordDto> getByEnvironment(String environment);
    double getCompliancePercentage(String environment);
    void recordCompliance(Long serverId, Long patchId, ComplianceStatus status, String verifiedBy);
    List<String> getEnvironments();
}
