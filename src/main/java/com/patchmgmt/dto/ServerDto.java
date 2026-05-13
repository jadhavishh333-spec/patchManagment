package com.patchmgmt.dto;

import com.patchmgmt.enums.ExecutionStrategyType;
import com.patchmgmt.enums.IisStopMode;
import com.patchmgmt.enums.OsType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerDto {

    private Long id;

    @NotBlank(message = "Server name is required")
    private String name;

    @NotBlank(message = "IP address or hostname is required")
    private String ipAddress;

    @NotNull(message = "OS type is required")
    private OsType osType;

    private String osVersion;
    private String environment;
    private String businessUnit;
    private String description;
    private boolean active = true;
    private Integer winrmPort;
    private Integer sshPort;
    private ExecutionStrategyType executionStrategy;
    private String cyberArkSafe;
    private String cyberArkObject;
    private Set<String> tags = new HashSet<>();

    /** Comma-separated service / App Pool / Site names to stop pre-patch and start post-patch. */
    private String preStopServices;

    /** Windows only: what IIS component type to stop. Defaults to APPPOOL. */
    private IisStopMode iisStopMode = IisStopMode.APPPOOL;
}
