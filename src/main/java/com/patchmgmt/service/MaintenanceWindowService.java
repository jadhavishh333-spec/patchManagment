package com.patchmgmt.service;

import com.patchmgmt.dto.MaintenanceWindowDto;
import com.patchmgmt.entity.MaintenanceWindow;
import com.patchmgmt.entity.Server;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MaintenanceWindowService {
    MaintenanceWindow create(MaintenanceWindowDto dto, String createdBy);
    MaintenanceWindow update(Long id, MaintenanceWindowDto dto);
    void delete(Long id);
    Optional<MaintenanceWindow> findById(Long id);
    List<MaintenanceWindow> findAll();
    List<MaintenanceWindow> findActive();
    boolean isInMaintenanceWindow(Server server, LocalDateTime time);
}
