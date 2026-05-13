package com.patchmgmt.service.impl;

import com.patchmgmt.dto.MaintenanceWindowDto;
import com.patchmgmt.entity.AppUser;
import com.patchmgmt.entity.MaintenanceWindow;
import com.patchmgmt.entity.Server;
import com.patchmgmt.exception.ResourceNotFoundException;
import com.patchmgmt.repository.MaintenanceWindowRepository;
import com.patchmgmt.repository.UserRepository;
import com.patchmgmt.service.MaintenanceWindowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MaintenanceWindowServiceImpl implements MaintenanceWindowService {

    private final MaintenanceWindowRepository maintenanceWindowRepository;
    private final UserRepository userRepository;

    @Override
    public MaintenanceWindow create(MaintenanceWindowDto dto, String createdBy) {
        AppUser user = userRepository.findByUsername(createdBy).orElse(null);
        MaintenanceWindow mw = MaintenanceWindow.builder()
            .name(dto.getName())
            .description(dto.getDescription())
            .windowType(dto.getWindowType())
            .startTime(dto.getStartTime())
            .endTime(dto.getEndTime())
            .dayOfWeek(dto.getDayOfWeek())
            .startDate(dto.getStartDate())
            .endDate(dto.getEndDate())
            .environment(dto.getEnvironment())
            .active(dto.isActive())
            .createdBy(user)
            .build();
        return maintenanceWindowRepository.save(mw);
    }

    @Override
    public MaintenanceWindow update(Long id, MaintenanceWindowDto dto) {
        MaintenanceWindow mw = maintenanceWindowRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("MaintenanceWindow", id));
        mw.setName(dto.getName());
        mw.setDescription(dto.getDescription());
        mw.setWindowType(dto.getWindowType());
        mw.setStartTime(dto.getStartTime());
        mw.setEndTime(dto.getEndTime());
        mw.setDayOfWeek(dto.getDayOfWeek());
        mw.setStartDate(dto.getStartDate());
        mw.setEndDate(dto.getEndDate());
        mw.setEnvironment(dto.getEnvironment());
        mw.setActive(dto.isActive());
        return maintenanceWindowRepository.save(mw);
    }

    @Override
    public void delete(Long id) {
        maintenanceWindowRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MaintenanceWindow> findById(Long id) {
        return maintenanceWindowRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceWindow> findAll() {
        return maintenanceWindowRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceWindow> findActive() {
        return maintenanceWindowRepository.findByActiveTrue();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isInMaintenanceWindow(Server server, LocalDateTime time) {
        String env = server.getEnvironment() != null ? server.getEnvironment() : "";
        LocalTime currentTime = time.toLocalTime();
        java.time.DayOfWeek dow = time.getDayOfWeek();
        List<MaintenanceWindow> active = maintenanceWindowRepository
            .findActiveWindowsForNow(env, currentTime, dow);
        boolean inWindow = !active.isEmpty();
        log.debug("Maintenance window check for server {} at {}: {}", server.getName(), time, inWindow);
        return inWindow;
    }
}
