package com.patchmgmt.entity;

import com.patchmgmt.enums.ComplianceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "compliance_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"server", "patch"})
public class ComplianceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patch_id", nullable = false)
    private Patch patch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ComplianceStatus status;

    @Column(name = "compliance_date")
    private LocalDate complianceDate;

    @Column(name = "patch_applied_at")
    private LocalDateTime patchAppliedAt;

    @Column(name = "verified_by", length = 100)
    private String verifiedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (complianceDate == null) complianceDate = LocalDate.now();
    }
}
