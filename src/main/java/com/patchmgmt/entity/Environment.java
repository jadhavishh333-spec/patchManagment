package com.patchmgmt.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "environments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Environment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name; // PROD, DEV, STAGING, UAT

    @Column(length = 500)
    private String description;

    @Column(name = "business_unit", length = 100)
    private String businessUnit;

    @Column(length = 100)
    private String owner;

    @Column(length = 20)
    private String criticality; // CRITICAL, HIGH, MEDIUM, LOW

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
