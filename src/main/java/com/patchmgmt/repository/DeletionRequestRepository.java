package com.patchmgmt.repository;

import com.patchmgmt.entity.DeletionRequest;
import com.patchmgmt.enums.DeletionRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeletionRequestRepository extends JpaRepository<DeletionRequest, Long> {

    List<DeletionRequest> findByStatusOrderByRequestedAtDesc(DeletionRequestStatus status);

    Optional<DeletionRequest> findByEntityTypeAndEntityIdAndStatus(
            String entityType, Long entityId, DeletionRequestStatus status);

    boolean existsByEntityTypeAndEntityIdAndStatus(
            String entityType, Long entityId, DeletionRequestStatus status);
}
