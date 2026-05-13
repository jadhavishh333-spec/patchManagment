package com.patchmgmt.service.impl;

import com.patchmgmt.dto.ComplianceDashboardDto;
import com.patchmgmt.dto.ComplianceRecordDto;
import com.patchmgmt.entity.ComplianceRecord;
import com.patchmgmt.entity.Patch;
import com.patchmgmt.entity.Server;
import com.patchmgmt.enums.ComplianceStatus;
import com.patchmgmt.exception.ResourceNotFoundException;
import com.patchmgmt.repository.ComplianceRecordRepository;
import com.patchmgmt.repository.PatchRepository;
import com.patchmgmt.repository.ServerRepository;
import com.patchmgmt.service.ComplianceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ComplianceServiceImpl implements ComplianceService {

    private final ComplianceRecordRepository complianceRecordRepository;
    private final ServerRepository serverRepository;
    private final PatchRepository patchRepository;

    @Override
    @Transactional(readOnly = true)
    public ComplianceDashboardDto getComplianceDashboard() {
        long totalServers = serverRepository.countByActiveTrue();
        long compliant = complianceRecordRepository.countByStatus(ComplianceStatus.COMPLIANT);
        long nonCompliant = complianceRecordRepository.countByStatus(ComplianceStatus.NON_COMPLIANT);
        long unknown = totalServers - compliant - nonCompliant;

        double overallPct = totalServers > 0
            ? Math.round((double) compliant / totalServers * 1000.0) / 10.0
            : 0.0;

        List<String> environments = serverRepository.findDistinctEnvironments();
        Map<String, Double> complianceByEnv = new LinkedHashMap<>();
        Map<String, Map<ComplianceStatus, Long>> statusByEnv = new LinkedHashMap<>();

        for (String env : environments) {
            long total = serverRepository.countByEnvironment(env);
            long comp  = complianceRecordRepository.countByStatusAndServer_Environment(ComplianceStatus.COMPLIANT, env);
            double pct = total > 0 ? Math.round((double) comp / total * 1000.0) / 10.0 : 0.0;
            complianceByEnv.put(env, pct);

            Map<ComplianceStatus, Long> statusMap = new EnumMap<>(ComplianceStatus.class);
            for (ComplianceStatus s : ComplianceStatus.values()) {
                statusMap.put(s, complianceRecordRepository.countByStatusAndServer_Environment(s, env));
            }
            statusByEnv.put(env, statusMap);
        }

        List<Server> nonCompliantServers = serverRepository.findByComplianceStatus(ComplianceStatus.NON_COMPLIANT);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");
        List<ComplianceDashboardDto.ServerComplianceSummary> nonCompliantList = nonCompliantServers.stream()
            .map(s -> ComplianceDashboardDto.ServerComplianceSummary.builder()
                .serverId(s.getId())
                .serverName(s.getName())
                .ipAddress(s.getIpAddress())
                .environment(s.getEnvironment())
                .status(s.getComplianceStatus())
                .lastPatchDate(s.getLastPatchDate() != null ? s.getLastPatchDate().format(fmt) : "Never")
                .build())
            .collect(Collectors.toList());

        return ComplianceDashboardDto.builder()
            .totalServers(totalServers)
            .compliantServers(compliant)
            .nonCompliantServers(nonCompliant)
            .unknownServers(Math.max(0, unknown))
            .overallCompliancePercentage(overallPct)
            .complianceByEnvironment(complianceByEnv)
            .statusByEnvironment(statusByEnv)
            .nonCompliantServerList(nonCompliantList)
            .recentlyPatchedServers(Collections.emptyList())
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceRecordDto> getByServer(Long serverId) {
        return complianceRecordRepository.findLatestByServerId(serverId)
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceRecordDto> getByEnvironment(String environment) {
        return complianceRecordRepository.findByServer_Environment(environment)
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public double getCompliancePercentage(String environment) {
        long total = serverRepository.countByEnvironment(environment);
        if (total == 0) return 0.0;
        long compliant = complianceRecordRepository.countByStatusAndServer_Environment(ComplianceStatus.COMPLIANT, environment);
        return Math.round((double) compliant / total * 1000.0) / 10.0;
    }

    @Override
    public void recordCompliance(Long serverId, Long patchId, ComplianceStatus status, String verifiedBy) {
        Server server = serverRepository.findById(serverId)
            .orElseThrow(() -> new ResourceNotFoundException("Server", serverId));
        Patch patch = patchRepository.findById(patchId)
            .orElseThrow(() -> new ResourceNotFoundException("Patch", patchId));

        ComplianceRecord record = ComplianceRecord.builder()
            .server(server)
            .patch(patch)
            .status(status)
            .complianceDate(LocalDate.now())
            .verifiedBy(verifiedBy)
            .build();
        complianceRecordRepository.save(record);

        // Update server compliance status
        server.setComplianceStatus(status);
        server.setLastComplianceCheck(java.time.LocalDateTime.now());
        serverRepository.save(server);
        log.info("Compliance recorded: server={} patch={} status={}", server.getName(), patch.getPatchId(), status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getEnvironments() {
        return serverRepository.findDistinctEnvironments();
    }

    private ComplianceRecordDto toDto(ComplianceRecord r) {
        return ComplianceRecordDto.builder()
            .id(r.getId())
            .serverId(r.getServer().getId())
            .serverName(r.getServer().getName())
            .serverIp(r.getServer().getIpAddress())
            .environment(r.getServer().getEnvironment())
            .patchId(r.getPatch().getId())
            .patchTitle(r.getPatch().getTitle())
            .patchIdentifier(r.getPatch().getPatchId())
            .status(r.getStatus())
            .complianceDate(r.getComplianceDate())
            .patchAppliedAt(r.getPatchAppliedAt())
            .verifiedBy(r.getVerifiedBy())
            .notes(r.getNotes())
            .createdAt(r.getCreatedAt())
            .build();
    }
}
