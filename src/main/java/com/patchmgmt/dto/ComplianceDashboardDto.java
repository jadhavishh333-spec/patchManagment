package com.patchmgmt.dto;

import com.patchmgmt.enums.ComplianceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceDashboardDto {
    private long totalServers;
    private long compliantServers;
    private long nonCompliantServers;
    private long unknownServers;
    private double overallCompliancePercentage;

    // Environment breakdown: env -> compliance %
    private Map<String, Double> complianceByEnvironment;
    // Environment breakdown: env -> count by status
    private Map<String, Map<ComplianceStatus, Long>> statusByEnvironment;

    private List<ServerComplianceSummary> nonCompliantServerList;
    private List<ServerComplianceSummary> recentlyPatchedServers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServerComplianceSummary {
        private Long serverId;
        private String serverName;
        private String ipAddress;
        private String environment;
        private ComplianceStatus status;
        private String lastPatchDate;
    }
}
