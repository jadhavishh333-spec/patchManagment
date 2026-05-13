package com.patchmgmt.dto;

import com.patchmgmt.enums.PatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionLogDto {
    private Long id;
    private Long patchJobId;
    private String jobTitle;
    private Long serverId;
    private String serverName;
    private String serverIp;
    private String environment;
    private PatchStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String logOutput;
    private String errorMessage;
    private int exitCode;
    private int retryAttempt;
    private LocalDateTime createdAt;

    public long getDurationSeconds() {
        if (startedAt != null && completedAt != null) {
            return java.time.Duration.between(startedAt, completedAt).getSeconds();
        }
        return 0;
    }
}
