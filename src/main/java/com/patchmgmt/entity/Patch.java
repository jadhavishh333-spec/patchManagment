package com.patchmgmt.entity;

import com.patchmgmt.enums.OsType;
import com.patchmgmt.enums.PatchSeverity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "patches")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Patch {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "patch_id", unique = true, length = 50)
    private String patchId; // e.g. KB5031356 or CVE-2023-1234

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "os_type", nullable = false)
    private OsType osType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PatchSeverity severity;

    @Column(name = "release_date")
    private LocalDateTime releaseDate;

    @Column(name = "requires_reboot")
    private boolean requiresReboot = false;

    @Column(name = "install_command", length = 500)
    private String installCommand; // OS-specific command

    /**
     * Local filesystem path on the patch-management server where the manually
     * uploaded patch binary (e.g. .msu / .exe / .deb / .rpm) is stored.
     * Null for catalogue-only patches that rely on WSUS / apt / yum.
     */
    @Column(name = "file_path", length = 500)
    private String filePath;

    /**
     * Destination directory on the TARGET server where the file will be
     * copied before installation.
     *   Windows default : C:\Patches\
     *   Linux default   : /tmp/patches/
     * The trailing separator is normalised at deploy time.
     */
    @Column(name = "deploy_path", length = 500)
    private String deployPath;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
