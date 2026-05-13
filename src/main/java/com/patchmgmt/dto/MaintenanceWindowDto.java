package com.patchmgmt.dto;

import com.patchmgmt.enums.WindowType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceWindowDto {
    private Long id;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotNull(message = "Window type is required")
    private WindowType windowType;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    private DayOfWeek dayOfWeek;
    private LocalDate startDate;
    private LocalDate endDate;
    private String environment;
    private boolean active = true;
    private String createdByUsername;
    private String createdAt;
}
