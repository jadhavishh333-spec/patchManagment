package com.patchmgmt.dto;
import com.patchmgmt.enums.OsType;
import com.patchmgmt.enums.PatchSeverity;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class PatchDto {
    private Long id;
    @NotBlank @Size(max=200) private String title;
    private String patchId;
    private String description;
    @NotNull private OsType osType;
    @NotNull private PatchSeverity severity;
    private LocalDateTime releaseDate;
    private boolean requiresReboot;
    private String installCommand;

    /** Populated by the controller after saving the uploaded binary to disk. */
    private String filePath;

    /**
     * Where the binary should land on the target server.
     * Defaults: Windows → C:\Patches\   Linux → /tmp/patches/
     */
    private String deployPath;
}
