package com.patchmgmt.repository;
import com.patchmgmt.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findAllByOrderByPerformedAtDesc(Pageable pageable);
    List<AuditLog> findByEntityTypeAndEntityIdOrderByPerformedAtDesc(String entityType, Long entityId);
    List<AuditLog> findTop20ByOrderByPerformedAtDesc();
}
