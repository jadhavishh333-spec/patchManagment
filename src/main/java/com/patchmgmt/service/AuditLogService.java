package com.patchmgmt.service;
import com.patchmgmt.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
public interface AuditLogService {
    void log(String action, String entityType, Long entityId, String details, String username);
    Page<AuditLog> findAll(Pageable pageable);
    List<AuditLog> findRecent();
    List<AuditLog> findByEntity(String entityType, Long entityId);
}
