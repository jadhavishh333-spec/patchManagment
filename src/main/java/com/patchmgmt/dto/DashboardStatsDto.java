package com.patchmgmt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {
    // Server stats
    private long totalServers;
    private long activeServers;
    private long windowsServers;
    private long linuxServers;

    // Patch stats
    private long totalPatches;
    private long criticalPatches;

    // Job stats
    private long totalJobs;
    private long pendingJobs;
    private long completedJobs;
    private long failedJobs;
    private long inProgressJobs;
    private long cancelledJobs;

    // Compliance stats
    private long compliantServers;
    private long nonCompliantServers;
    private double overallCompliancePercentage;

    // Environment count
    private long prodServers;
    private long devServers;
    private long stagingServers;
}
