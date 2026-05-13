package com.patchmgmt.service.impl;

import com.patchmgmt.dto.ServerDto;
import com.patchmgmt.entity.Server;
import com.patchmgmt.enums.OsType;
import com.patchmgmt.repository.ServerRepository;
import com.patchmgmt.repository.UserRepository;
import com.patchmgmt.service.ServerService;
import com.patchmgmt.entity.AppUser;
import com.patchmgmt.enums.ApprovalStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ServerServiceImpl implements ServerService {

    private final ServerRepository serverRepository;
    private final UserRepository userRepository;

    @Override
    public Server save(ServerDto dto, String createdBy) {
        AppUser user = userRepository.findByUsername(createdBy).orElse(null);
        boolean isAdmin = user != null && user.getRole().name().equals("ROLE_ADMIN");

        // Guard: IP address must be unique across all servers
        if (serverRepository.existsByIpAddress(dto.getIpAddress())) {
            throw new IllegalArgumentException(
                "IP address '" + dto.getIpAddress() + "' is already registered to another server.");
        }
        
        Server server = Server.builder()
            .name(dto.getName()).ipAddress(dto.getIpAddress())
            .osType(dto.getOsType()).osVersion(dto.getOsVersion())
            .environment(dto.getEnvironment()).description(dto.getDescription())
            .businessUnit(dto.getBusinessUnit())
            .winrmPort(dto.getWinrmPort() != null ? dto.getWinrmPort() : 5985)
            .sshPort(dto.getSshPort() != null ? dto.getSshPort() : 22)
            .executionStrategy(dto.getExecutionStrategy() != null
                ? dto.getExecutionStrategy()
                : com.patchmgmt.enums.ExecutionStrategyType.MOCK)
            .cyberArkSafe(dto.getCyberArkSafe())
            .cyberArkObject(dto.getCyberArkObject())
            .tags(dto.getTags() != null ? dto.getTags() : new java.util.HashSet<>())
            .active(true)
            .approvalStatus(isAdmin ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING)
            .preStopServices(dto.getPreStopServices())
            .iisStopMode(dto.getIisStopMode() != null ? dto.getIisStopMode() : com.patchmgmt.enums.IisStopMode.APPPOOL)
            .createdBy(user)
            .build();
        return serverRepository.save(server);
    }

    @Override
    public Server update(Long id, ServerDto dto) {
        Server server = serverRepository.findById(id)
            .orElseThrow(() -> new com.patchmgmt.exception.ResourceNotFoundException("Server", id));

        // Guard: check IP conflict only when the IP actually changed
        String newIp = dto.getIpAddress();
        if (newIp != null && !newIp.equalsIgnoreCase(server.getIpAddress())) {
            serverRepository.findByIpAddress(newIp).ifPresent(conflict -> {
                throw new IllegalArgumentException(
                    "IP address '" + newIp + "' is already assigned to server '" + conflict.getName() + "'.");
            });
        }

        server.setName(dto.getName());
        server.setIpAddress(newIp);
        server.setOsType(dto.getOsType());
        server.setOsVersion(dto.getOsVersion());
        server.setEnvironment(dto.getEnvironment());
        server.setDescription(dto.getDescription());
        server.setBusinessUnit(dto.getBusinessUnit());
        if (dto.getWinrmPort() != null) server.setWinrmPort(dto.getWinrmPort());
        if (dto.getSshPort() != null)   server.setSshPort(dto.getSshPort());
        if (dto.getExecutionStrategy() != null) server.setExecutionStrategy(dto.getExecutionStrategy());
        if (dto.getCyberArkSafe() != null)   server.setCyberArkSafe(dto.getCyberArkSafe());
        if (dto.getCyberArkObject() != null) server.setCyberArkObject(dto.getCyberArkObject());
        if (dto.getTags() != null)    server.setTags(dto.getTags());
        // Service lifecycle fields
        server.setPreStopServices(dto.getPreStopServices());
        if (dto.getIisStopMode() != null) server.setIisStopMode(dto.getIisStopMode());
        return serverRepository.save(server);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Server> findById(Long id) { return serverRepository.findById(id); }

    @Override
    @Transactional(readOnly = true)
    public List<Server> findAll() { return serverRepository.findAll(); }

    @Override
    @Transactional(readOnly = true)
    public List<Server> findActive() { return serverRepository.findByActiveTrue(); }

    @Override
    @Transactional(readOnly = true)
    public List<Server> findByOsType(OsType osType) { return serverRepository.findByOsType(osType); }

    @Override
    public void delete(Long id) { serverRepository.deleteById(id); }

    @Override
    public void toggleActive(Long id) {
        Server server = serverRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Server not found"));
        server.setActive(!server.isActive());
        serverRepository.save(server);
    }
    
    @Override
    public void approveServer(Long id, String approvedBy) {
        Server server = serverRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Server not found"));
        server.setApprovalStatus(ApprovalStatus.APPROVED);
        serverRepository.save(server);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Server> findPendingApproval() {
        return serverRepository.findByApprovalStatus(ApprovalStatus.PENDING);
    }
}
