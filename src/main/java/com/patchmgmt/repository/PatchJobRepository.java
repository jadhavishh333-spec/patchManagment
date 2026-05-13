package com.patchmgmt.repository;

import com.patchmgmt.entity.PatchJob;
import com.patchmgmt.enums.PatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PatchJobRepository extends JpaRepository<PatchJob, Long> {
    List<PatchJob> findByStatus(PatchStatus status);
    List<PatchJob> findByServer_Id(Long serverId);
    List<PatchJob> findByCreatedBy_Id(Long userId);
    List<PatchJob> findTop10ByOrderByCreatedAtDesc();
    long countByStatus(PatchStatus status);

    @Query("SELECT j FROM PatchJob j WHERE j.status = :status AND j.scheduledAt <= :now")
    List<PatchJob> findDueJobs(@Param("status") PatchStatus status, @Param("now") LocalDateTime now);

    @Query("SELECT j FROM PatchJob j WHERE j.maintenanceWindow.id = :windowId")
    List<PatchJob> findByMaintenanceWindowId(@Param("windowId") Long windowId);

    @Query("SELECT COUNT(j) FROM PatchJob j WHERE j.status = :status AND j.server.environment = :env")
    long countByStatusAndEnvironment(@Param("status") PatchStatus status, @Param("env") String environment);

    @Query("SELECT j FROM PatchJob j WHERE j.status = :status AND j.retryCount < j.maxRetries")
    List<PatchJob> findRetryableJobs(@Param("status") PatchStatus status);

    @Query("SELECT j FROM PatchJob j ORDER BY j.createdAt DESC")
    List<PatchJob> findAllOrderedByCreatedAtDesc();
}
