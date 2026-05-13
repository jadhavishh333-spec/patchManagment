package com.patchmgmt.service;

import com.patchmgmt.dto.PatchJobDto;
import com.patchmgmt.entity.PatchJob;
import com.patchmgmt.enums.PatchStatus;

import java.util.List;
import java.util.Optional;

public interface PatchJobService {
    PatchJob create(PatchJobDto dto, String createdBy);
    PatchJob approve(Long id, String approvedBy);
    PatchJob cancel(Long id);
    PatchJob retryJob(Long id, String requestedBy);
    void execute(Long id);
    void delete(Long id);
    Optional<PatchJob> findById(Long id);
    List<PatchJob> findAll();
    List<PatchJob> findByStatus(PatchStatus status);
    List<PatchJob> findByServerId(Long serverId);
    List<PatchJob> findByUser(String username);
    List<PatchJob> findRecent();
}
