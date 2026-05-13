package com.patchmgmt.service.impl;

import com.patchmgmt.dto.PatchDto;
import com.patchmgmt.entity.Patch;
import com.patchmgmt.enums.OsType;
import com.patchmgmt.repository.PatchRepository;
import com.patchmgmt.repository.UserRepository;
import com.patchmgmt.service.PatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PatchServiceImpl implements PatchService {

    private final PatchRepository patchRepository;
    private final UserRepository  userRepository;

    @Value("${patch.upload.dir:./patch-uploads}")
    private String uploadDir;

    // ── File upload ────────────────────────────────────────────────────────────

    /**
     * Persists a manually-uploaded patch binary to the configured upload directory.
     *
     * @param file the uploaded multipart file (e.g. .msu, .exe, .deb, .rpm)
     * @return absolute path of the saved file on this server
     */
    public String savePatchFile(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);

            String original  = file.getOriginalFilename();
            String safeName  = (original != null ? original : "patch-file")
                                .replaceAll("[^a-zA-Z0-9._\\-]", "_");
            // Prefix with timestamp to avoid collisions
            String finalName = System.currentTimeMillis() + "_" + safeName;
            Path dest = dir.resolve(finalName);
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

            log.info("Patch file saved: {} ({} bytes)", dest.toAbsolutePath(), file.getSize());
            return dest.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save uploaded patch file: " + e.getMessage(), e);
        }
    }

    // ── CRUD ───────────────────────────────────────────────────────────────────

    @Override
    public Patch save(PatchDto dto, String createdBy) {
        // Auto-generate a unique patch ID if the user left it blank
        String patchId = (dto.getPatchId() != null && !dto.getPatchId().isBlank())
            ? dto.getPatchId()
            : "UPLOAD-" + System.currentTimeMillis();

        Patch patch = Patch.builder()
            .title(dto.getTitle()).patchId(patchId)
            .description(dto.getDescription()).osType(dto.getOsType())
            .severity(dto.getSeverity()).releaseDate(dto.getReleaseDate())
            .requiresReboot(dto.isRequiresReboot()).installCommand(dto.getInstallCommand())
            .filePath(dto.getFilePath())
            .deployPath(resolveDeployPath(dto))
            .createdBy(userRepository.findByUsername(createdBy).orElse(null))
            .build();
        return patchRepository.save(patch);
    }

    @Override
    public Patch update(Long id, PatchDto dto) {
        Patch patch = patchRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Patch not found"));
        patch.setTitle(dto.getTitle());           patch.setPatchId(dto.getPatchId());
        patch.setDescription(dto.getDescription()); patch.setOsType(dto.getOsType());
        patch.setSeverity(dto.getSeverity());      patch.setReleaseDate(dto.getReleaseDate());
        patch.setRequiresReboot(dto.isRequiresReboot()); patch.setInstallCommand(dto.getInstallCommand());
        patch.setDeployPath(resolveDeployPath(dto));
        // Only overwrite filePath if a new file was uploaded (dto.getFilePath() != null)
        if (dto.getFilePath() != null && !dto.getFilePath().isBlank()) {
            patch.setFilePath(dto.getFilePath());
        }
        return patchRepository.save(patch);
    }

    @Override @Transactional(readOnly = true)
    public Optional<Patch> findById(Long id) { return patchRepository.findById(id); }

    @Override @Transactional(readOnly = true)
    public List<Patch> findAll() { return patchRepository.findAll(); }

    @Override @Transactional(readOnly = true)
    public List<Patch> findByOsType(OsType osType) { return patchRepository.findByOsType(osType); }

    @Override
    public void delete(Long id) { patchRepository.deleteById(id); }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * If the user didn't supply a deploy path, default based on OS type.
     * Windows: C:\Patches\   Linux: /tmp/patches/
     */
    private String resolveDeployPath(PatchDto dto) {
        if (dto.getDeployPath() != null && !dto.getDeployPath().isBlank()) {
            return dto.getDeployPath();
        }
        if (dto.getFilePath() == null || dto.getFilePath().isBlank()) {
            return null; // no file upload — deploy path irrelevant
        }
        return dto.getOsType() == OsType.WINDOWS ? "C:\\Patches\\" : "/tmp/patches/";
    }
}

