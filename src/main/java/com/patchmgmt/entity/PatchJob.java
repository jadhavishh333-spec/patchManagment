package com.patchmgmt.entity;

import com.patchmgmt.enums.ExecutionStrategyType;
import com.patchmgmt.enums.PatchStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "patch_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"server", "patch", "createdBy", "approvedBy", "maintenanceWindow"})
public class PatchJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patch_id", nullable = false)
    private Patch patch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PatchStatus status;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "execution_log", columnDefinition = "TEXT")
    private String executionLog;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count")
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "max_retries")
    @Builder.Default
    private int maxRetries = 3;

    @Column(name = "timeout_minutes")
    @Builder.Default
    private int timeoutMinutes = 30;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_strategy", length = 20)
    private ExecutionStrategyType executionStrategy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_window_id")
    private MaintenanceWindow maintenanceWindow;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private AppUser approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = PatchStatus.PENDING;
    }
}
