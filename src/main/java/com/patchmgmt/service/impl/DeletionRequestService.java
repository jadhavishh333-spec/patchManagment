package com.patchmgmt.service.impl;

import com.patchmgmt.entity.AppUser;
import com.patchmgmt.entity.DeletionRequest;
import com.patchmgmt.enums.DeletionRequestStatus;
import com.patchmgmt.repository.DeletionRequestRepository;
import com.patchmgmt.repository.PatchRepository;
import com.patchmgmt.repository.ServerRepository;
import com.patchmgmt.repository.UserRepository;
import com.patchmgmt.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeletionRequestService {

    private final DeletionRequestRepository repo;
    private final UserRepository userRepository;
    private final ServerRepository serverRepository;
    private final PatchRepository patchRepository;
    private final AuditLogService auditLogService;

    /** Create a new deletion request (USER or ADMIN can request) */
    @Transactional
    public DeletionRequest request(String entityType, Long entityId,
                                   String entityName, String requestedByUsername) {

        // Guard: only one pending request per entity at a time
        if (repo.existsByEntityTypeAndEntityIdAndStatus(
                entityType, entityId, DeletionRequestStatus.PENDING)) {
            throw new IllegalStateException("A deletion request for this " + entityType
                + " is already pending admin approval.");
        }

        AppUser user = userRepository.findByUsername(requestedByUsername).orElse(null);

        DeletionRequest dr = DeletionRequest.builder()
                .entityType(entityType)
                .entityId(entityId)
                .entityName(entityName)
                .status(DeletionRequestStatus.PENDING)
                .requestedBy(user)
                .build();

        DeletionRequest saved = repo.save(dr);
        auditLogService.log("REQUEST_DELETE", entityType, entityId,
                "Deletion requested for " + entityName + " by " + requestedByUsername,
                requestedByUsername);
        log.info("Deletion request #{} created for {} #{} by {}", saved.getId(),
                entityType, entityId, requestedByUsername);
        return saved;
    }

    /** Admin approves — actually deletes the entity */
    @Transactional
    public void approve(Long requestId, String adminName) {
        DeletionRequest dr = repo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Deletion request not found: " + requestId));

        if (dr.getStatus() != DeletionRequestStatus.PENDING) {
            throw new IllegalStateException("Request is already " + dr.getStatus());
        }

        // Perform the actual deletion
        switch (dr.getEntityType()) {
            case "SERVER" -> serverRepository.deleteById(dr.getEntityId());
            case "PATCH"  -> patchRepository.deleteById(dr.getEntityId());
            default -> throw new IllegalArgumentException("Unknown entity type: " + dr.getEntityType());
        }

        dr.setStatus(DeletionRequestStatus.APPROVED);
        dr.setResolvedBy(adminName);
        dr.setResolvedAt(LocalDateTime.now());
        repo.save(dr);

        auditLogService.log("APPROVE_DELETE", dr.getEntityType(), dr.getEntityId(),
                "Deletion approved for " + dr.getEntityName() + " by " + adminName, adminName);
        log.info("Deletion request #{} approved by {} — {} #{} deleted",
                requestId, adminName, dr.getEntityType(), dr.getEntityId());
    }

    /** Admin rejects — entity survives */
    @Transactional
    public void reject(Long requestId, String adminName, String reason) {
        DeletionRequest dr = repo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Deletion request not found: " + requestId));

        if (dr.getStatus() != DeletionRequestStatus.PENDING) {
            throw new IllegalStateException("Request is already " + dr.getStatus());
        }

        dr.setStatus(DeletionRequestStatus.REJECTED);
        dr.setResolvedBy(adminName);
        dr.setResolvedAt(LocalDateTime.now());
        dr.setRejectionReason(reason);
        repo.save(dr);

        auditLogService.log("REJECT_DELETE", dr.getEntityType(), dr.getEntityId(),
                "Deletion rejected for " + dr.getEntityName() + " by " + adminName
                    + ". Reason: " + reason, adminName);
        log.info("Deletion request #{} rejected by {}", requestId, adminName);
    }

    public List<DeletionRequest> findPending() {
        return repo.findByStatusOrderByRequestedAtDesc(DeletionRequestStatus.PENDING);
    }

    public long countPending() {
        return repo.findByStatusOrderByRequestedAtDesc(DeletionRequestStatus.PENDING).size();
    }

    public boolean hasPendingRequest(String entityType, Long entityId) {
        return repo.existsByEntityTypeAndEntityIdAndStatus(
                entityType, entityId, DeletionRequestStatus.PENDING);
    }

    public Optional<DeletionRequest> findPendingFor(String entityType, Long entityId) {
        return repo.findByEntityTypeAndEntityIdAndStatus(
                entityType, entityId, DeletionRequestStatus.PENDING);
    }
}
