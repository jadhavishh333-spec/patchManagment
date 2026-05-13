package com.patchmgmt.repository;

import com.patchmgmt.entity.ExecutionLog;
import com.patchmgmt.enums.PatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, Long> {
    List<ExecutionLog> findByPatchJobId(Long jobId);
    List<ExecutionLog> findByServerId(Long serverId);
    List<ExecutionLog> findByPatchJobIdOrderByCreatedAtDesc(Long jobId);
    long countByStatus(PatchStatus status);
    long countByPatchJobId(Long jobId);
    void deleteByPatchJobId(Long jobId);
}
