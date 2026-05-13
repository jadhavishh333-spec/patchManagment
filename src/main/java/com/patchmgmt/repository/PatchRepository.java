package com.patchmgmt.repository;
import com.patchmgmt.entity.Patch;
import com.patchmgmt.enums.OsType;
import com.patchmgmt.enums.PatchSeverity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PatchRepository extends JpaRepository<Patch, Long> {
    List<Patch> findByOsType(OsType osType);
    List<Patch> findBySeverity(PatchSeverity severity);
    boolean existsByPatchId(String patchId);
    long countBySeverity(PatchSeverity severity);
}
