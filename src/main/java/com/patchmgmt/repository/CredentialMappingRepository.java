package com.patchmgmt.repository;

import com.patchmgmt.entity.CredentialMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CredentialMappingRepository extends JpaRepository<CredentialMapping, Long> {
    Optional<CredentialMapping> findByServer_Id(Long serverId);
    Optional<CredentialMapping> findByEnvironment(String environment);
    boolean existsByServer_Id(Long serverId);
}
