package com.patchmgmt.service;
import com.patchmgmt.dto.ServerDto;
import com.patchmgmt.entity.Server;
import com.patchmgmt.enums.OsType;
import java.util.List;
import java.util.Optional;
public interface ServerService {
    Server save(ServerDto dto, String createdBy);
    Server update(Long id, ServerDto dto);
    Optional<Server> findById(Long id);
    List<Server> findAll();
    List<Server> findActive();
    List<Server> findByOsType(OsType osType);
    void delete(Long id);
    void toggleActive(Long id);
    void approveServer(Long id, String approvedBy);
    List<Server> findPendingApproval();
}
