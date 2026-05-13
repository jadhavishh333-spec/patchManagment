package com.patchmgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatchJobDto {

    private Long id;

    @NotBlank(message = "Job title is required")
    private String title;

    @NotNull(message = "Server is required")
    private Long serverId;

    @NotNull(message = "Patch is required")
    private Long patchId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime scheduledAt;

    private String description;
    private Long maintenanceWindowId;
}
