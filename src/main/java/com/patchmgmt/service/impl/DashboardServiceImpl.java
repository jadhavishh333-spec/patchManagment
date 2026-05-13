package com.patchmgmt.service.impl;

import com.patchmgmt.dto.DashboardStatsDto;
import com.patchmgmt.enums.ComplianceStatus;
import com.patchmgmt.enums.OsType;
import com.patchmgmt.enums.PatchSeverity;
import com.patchmgmt.enums.PatchStatus;
import com.patchmgmt.repository.*;
import com.patchmgmt.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final ServerRepository serverRepository;
    private final PatchRepository patchRepository;
    private final PatchJobRepository patchJobRepository;
    private final ComplianceRecordRepository complianceRecordRepository;

    @Override
    public DashboardStatsDto getStats() {
        long totalServers = serverRepository.count();
        long compliant    = complianceRecordRepository.countByStatus(ComplianceStatus.COMPLIANT);
        long nonCompliant = complianceRecordRepository.countByStatus(ComplianceStatus.NON_COMPLIANT);
        double compliancePct = totalServers > 0
            ? Math.round((double) compliant / totalServers * 1000.0) / 10.0
            : 0.0;

        return DashboardStatsDto.builder()
            // Servers
            .totalServers(totalServers)
            .activeServers(serverRepository.countByActiveTrue())
            .windowsServers(serverRepository.countByOsType(OsType.WINDOWS))
            .linuxServers(serverRepository.countByOsType(OsType.LINUX))
            // Patches
            .totalPatches(patchRepository.count())
            .criticalPatches(patchRepository.countBySeverity(PatchSeverity.CRITICAL))
            // Jobs
            .totalJobs(patchJobRepository.count())
            .pendingJobs(patchJobRepository.countByStatus(PatchStatus.PENDING))
            .completedJobs(patchJobRepository.countByStatus(PatchStatus.COMPLETED))
            .failedJobs(patchJobRepository.countByStatus(PatchStatus.FAILED))
            .inProgressJobs(patchJobRepository.countByStatus(PatchStatus.IN_PROGRESS))
            .cancelledJobs(patchJobRepository.countByStatus(PatchStatus.CANCELLED))
            // Compliance
            .compliantServers(compliant)
            .nonCompliantServers(nonCompliant)
            .overallCompliancePercentage(compliancePct)
            // By Environment
            .prodServers(serverRepository.countByEnvironment("PROD"))
            .devServers(serverRepository.countByEnvironment("DEV"))
            .stagingServers(serverRepository.countByEnvironment("STAGING"))
            .build();
    }
}
