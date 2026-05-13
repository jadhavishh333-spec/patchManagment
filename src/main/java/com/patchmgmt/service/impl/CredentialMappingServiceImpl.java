package com.patchmgmt.service.impl;

import com.patchmgmt.config.IntegrationProperties;
import com.patchmgmt.dto.CredentialMappingDto;
import com.patchmgmt.entity.AppUser;
import com.patchmgmt.entity.CredentialMapping;
import com.patchmgmt.entity.Server;
import com.patchmgmt.exception.ResourceNotFoundException;
import com.patchmgmt.exception.ValidationException;
import com.patchmgmt.integration.credential.AesEncryptionUtil;
import com.patchmgmt.repository.CredentialMappingRepository;
import com.patchmgmt.repository.ServerRepository;
import com.patchmgmt.repository.UserRepository;
import com.patchmgmt.service.CredentialMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CredentialMappingServiceImpl implements CredentialMappingService {

    private final CredentialMappingRepository credentialMappingRepository;
    private final ServerRepository serverRepository;
    private final UserRepository userRepository;
    private final IntegrationProperties integrationProperties;

    @Override
    public CredentialMapping create(CredentialMappingDto dto, String createdBy) {
        Server server = serverRepository.findById(dto.getServerId())
            .orElseThrow(() -> new ResourceNotFoundException("Server", dto.getServerId()));
        AppUser user = userRepository.findByUsername(createdBy).orElse(null);

        boolean cyberArkMode = integrationProperties.getCyberark().isEnabled();

        String safe, object, username;
        if (cyberArkMode) {
            if (dto.getCyberArkSafe() == null || dto.getCyberArkSafe().isBlank())
                throw new ValidationException("CyberArk Safe name is required");
            if (dto.getCyberArkObject() == null || dto.getCyberArkObject().isBlank())
                throw new ValidationException("CyberArk Object name is required");
            safe     = dto.getCyberArkSafe();
            object   = dto.getCyberArkObject();
            username = dto.getCyberArkUsername();
        } else {
            // Local SSH mode: reuse cyberark columns
            if (dto.getSshUsername() == null || dto.getSshUsername().isBlank())
                throw new ValidationException("SSH username is required");
            if (dto.getSshPassword() == null || dto.getSshPassword().isBlank())
                throw new ValidationException("SSH password is required");
            safe     = dto.getSshUsername();   // stored in cyberark_safe column
            String encKey = integrationProperties.getLocalCredentials().getEncryptionKey();
            object   = AesEncryptionUtil.encrypt(dto.getSshPassword(), encKey);  // stored in cyberark_object column
            username = dto.getSshUsername();
        }

        CredentialMapping mapping = CredentialMapping.builder()
            .server(server)
            .cyberArkSafe(safe)
            .cyberArkObject(object)
            .cyberArkUsername(username)
            .environment(dto.getEnvironment())
            .notes(dto.getNotes())
            .createdBy(user)
            .build();
        log.info("Credential mapping created for server: {} [{}]",
            server.getName(), cyberArkMode ? "CyberArk" : "Local SSH");
        return credentialMappingRepository.save(mapping);
    }

    @Override
    public CredentialMapping update(Long id, CredentialMappingDto dto) {
        CredentialMapping mapping = credentialMappingRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CredentialMapping", id));

        boolean cyberArkMode = integrationProperties.getCyberark().isEnabled();

        if (cyberArkMode) {
            if (dto.getCyberArkSafe() == null || dto.getCyberArkSafe().isBlank())
                throw new ValidationException("CyberArk Safe name is required");
            if (dto.getCyberArkObject() == null || dto.getCyberArkObject().isBlank())
                throw new ValidationException("CyberArk Object name is required");
            mapping.setCyberArkSafe(dto.getCyberArkSafe());
            mapping.setCyberArkObject(dto.getCyberArkObject());
            mapping.setCyberArkUsername(dto.getCyberArkUsername());
        } else {
            // Local SSH mode
            if (dto.getSshUsername() != null && !dto.getSshUsername().isBlank()) {
                mapping.setCyberArkSafe(dto.getSshUsername());    // username column
                mapping.setCyberArkUsername(dto.getSshUsername());
            }
            // Only re-encrypt password if a new one was provided
            if (dto.getSshPassword() != null && !dto.getSshPassword().isBlank()) {
                String encKey = integrationProperties.getLocalCredentials().getEncryptionKey();
                mapping.setCyberArkObject(AesEncryptionUtil.encrypt(dto.getSshPassword(), encKey));
            }
        }

        mapping.setEnvironment(dto.getEnvironment());
        mapping.setNotes(dto.getNotes());
        return credentialMappingRepository.save(mapping);
    }

    @Override
    public void delete(Long id) {
        credentialMappingRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CredentialMapping> findById(Long id) {
        return credentialMappingRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CredentialMapping> findByServerId(Long serverId) {
        return credentialMappingRepository.findByServer_Id(serverId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CredentialMapping> findAll() {
        return credentialMappingRepository.findAll();
    }
}
