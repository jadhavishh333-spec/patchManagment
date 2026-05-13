package com.patchmgmt.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "credential_mappings",
       uniqueConstraints = @UniqueConstraint(columnNames = "server_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"server", "createdBy"})
public class CredentialMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false, unique = true)
    private Server server;

    @Column(name = "cyberark_safe", nullable = false, length = 200)
    private String cyberArkSafe;

    @Column(name = "cyberark_object", nullable = false, length = 200)
    private String cyberArkObject;

    @Column(name = "cyberark_username", length = 100)
    private String cyberArkUsername;

    @Column(name = "environment", length = 50)
    private String environment;

    @Column(name = "last_fetched")
    private LocalDateTime lastFetched;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
