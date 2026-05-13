package com.patchmgmt.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(length = 1000)
    private String details;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private AppUser performedBy;

    @Column(name = "performed_at")
    private LocalDateTime performedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @PrePersist
    protected void onCreate() { performedAt = LocalDateTime.now(); }
}
