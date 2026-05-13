package com.patchmgmt.service;

import com.patchmgmt.dto.CredentialMappingDto;
import com.patchmgmt.entity.CredentialMapping;

import java.util.List;
import java.util.Optional;

public interface CredentialMappingService {
    CredentialMapping create(CredentialMappingDto dto, String createdBy);
    CredentialMapping update(Long id, CredentialMappingDto dto);
    void delete(Long id);
    Optional<CredentialMapping> findById(Long id);
    Optional<CredentialMapping> findByServerId(Long serverId);
    List<CredentialMapping> findAll();
}
