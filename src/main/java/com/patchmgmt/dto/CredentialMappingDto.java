package com.patchmgmt.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialMappingDto {
    private Long id;

    @NotNull(message = "Server is required")
    private Long serverId;
    private String serverName;
    private String serverIp;

    // ── CyberArk mode fields ──────────────────────────────────────────────────
    // Required when integration.cyberark.enabled=true (validated at service level)
    private String cyberArkSafe;
    private String cyberArkObject;
    private String cyberArkUsername;

    // ── Local SSH mode fields (dev/test without CyberArk) ────────────────────
    // Required when integration.cyberark.enabled=false (validated at service level)
    private String sshUsername;
    /** Write-only: AES-256 encrypted before DB storage. Never returned in responses. */
    private String sshPassword;

    // ── Common fields ─────────────────────────────────────────────────────────
    private String environment;
    private String notes;
    private String lastFetched;
    private String createdAt;
    private String createdByUsername;
    // NOTE: raw password is never returned in this DTO
}
