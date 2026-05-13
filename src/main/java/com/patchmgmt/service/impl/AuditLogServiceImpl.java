package com.patchmgmt.service.impl;
import com.patchmgmt.entity.AuditLog;
import com.patchmgmt.repository.AuditLogRepository;
import com.patchmgmt.repository.UserRepository;
import com.patchmgmt.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional
public class AuditLogServiceImpl implements AuditLogService {
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    @Override
    public void log(String action, String entityType, Long entityId, String details, String username) {
        AuditLog log = AuditLog.builder().action(action).entityType(entityType)
            .entityId(entityId).details(details)
            .performedBy(userRepository.findByUsername(username).orElse(null)).build();
        auditLogRepository.save(log);
    }
    @Override @Transactional(readOnly=true)
    public Page<AuditLog> findAll(Pageable pageable) { return auditLogRepository.findAllByOrderByPerformedAtDesc(pageable); }
    @Override @Transactional(readOnly=true)
    public List<AuditLog> findRecent() { return auditLogRepository.findTop20ByOrderByPerformedAtDesc(); }
    @Override @Transactional(readOnly=true)
    public List<AuditLog> findByEntity(String entityType, Long entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByPerformedAtDesc(entityType, entityId);
    }
}
