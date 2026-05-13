package com.patchmgmt.dto;

import com.patchmgmt.enums.ComplianceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceRecordDto {
    private Long id;
    private Long serverId;
    private String serverName;
    private String serverIp;
    private String environment;
    private Long patchId;
    private String patchTitle;
    private String patchIdentifier;
    private ComplianceStatus status;
    private LocalDate complianceDate;
    private LocalDateTime patchAppliedAt;
    private String verifiedBy;
    private String notes;
    private LocalDateTime createdAt;
}
