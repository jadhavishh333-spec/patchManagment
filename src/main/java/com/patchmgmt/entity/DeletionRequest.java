package com.patchmgmt.entity;

import com.patchmgmt.enums.DeletionRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "deletion_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeletionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "SERVER" or "PATCH" */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** Human-readable label (server name / patch title) shown in the approvals UI */
    @Column(name = "entity_name", nullable = false, length = 200)
    private String entityName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DeletionRequestStatus status = DeletionRequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private AppUser requestedBy;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "resolved_by", length = 50)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @PrePersist
    protected void onCreate() {
        requestedAt = LocalDateTime.now();
    }
}
