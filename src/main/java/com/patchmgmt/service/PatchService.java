package com.patchmgmt.service;
import com.patchmgmt.dto.PatchDto;
import com.patchmgmt.entity.Patch;
import com.patchmgmt.enums.OsType;
import java.util.List;
import java.util.Optional;
public interface PatchService {
    Patch save(PatchDto dto, String createdBy);
    Patch update(Long id, PatchDto dto);
    Optional<Patch> findById(Long id);
    List<Patch> findAll();
    List<Patch> findByOsType(OsType osType);
    void delete(Long id);
}
