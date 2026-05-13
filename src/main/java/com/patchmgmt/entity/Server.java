package com.patchmgmt.entity;

import com.patchmgmt.enums.ApprovalStatus;
import com.patchmgmt.enums.ComplianceStatus;
import com.patchmgmt.enums.ExecutionStrategyType;
import com.patchmgmt.enums.IisStopMode;
import com.patchmgmt.enums.OsType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "servers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"createdBy"})
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank
    @Pattern(regexp = "^([0-9]{1,3}\\.){3}[0-9]{1,3}$|^[a-zA-Z0-9.-]+$",
             message = "Must be a valid IP address or hostname")
    @Column(name = "ip_address", nullable = false, unique = true, length = 100)
    private String ipAddress;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "os_type", nullable = false)
    private OsType osType;

    @Column(name = "os_version", length = 100)
    private String osVersion;

    @Column(length = 100)
    private String environment; // PROD, DEV, STAGING, UAT

    @Column(name = "business_unit", length = 100)
    private String businessUnit;

    @Column(length = 500)
    private String description;

    /**
     * Comma-separated list of services to stop before patching and restart after.
     * Windows: IIS Application Pool names (e.g. "DefaultAppPool,MyAPI")
     *          OR IIS Site names depending on iisStopMode.
     * Linux:   systemd/init service names (e.g. "nginx,tomcat9,myapp").
     */
    @Column(name = "pre_stop_services", columnDefinition = "TEXT")
    private String preStopServices;

    /**
     * For Windows servers: controls whether to stop App Pools, Sites, or Both.
     * Ignored for Linux (always uses systemctl stop &lt;service&gt;).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "iis_stop_mode", length = 20)
    private IisStopMode iisStopMode = IisStopMode.APPPOOL;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", length = 30)
    private ApprovalStatus approvalStatus = ApprovalStatus.APPROVED;

    @Column(name = "winrm_port")
    private Integer winrmPort = 5985;

    @Column(name = "ssh_port")
    private Integer sshPort = 22;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_strategy", length = 20)
    private ExecutionStrategyType executionStrategy = ExecutionStrategyType.MOCK;

    @Column(name = "cyberark_safe", length = 200)
    private String cyberArkSafe;

    @Column(name = "cyberark_object", length = 200)
    private String cyberArkObject;

    @Enumerated(EnumType.STRING)
    @Column(name = "compliance_status", length = 30)
    private ComplianceStatus complianceStatus = ComplianceStatus.UNKNOWN;

    @Column(name = "last_compliance_check")
    private LocalDateTime lastComplianceCheck;

    @Column(name = "last_patch_date")
    private LocalDateTime lastPatchDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "server_tags", joinColumns = @JoinColumn(name = "server_id"))
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (complianceStatus == null) complianceStatus = ComplianceStatus.UNKNOWN;
        if (executionStrategy == null) executionStrategy = ExecutionStrategyType.MOCK;
        if (approvalStatus == null) approvalStatus = ApprovalStatus.APPROVED;
    }
}
