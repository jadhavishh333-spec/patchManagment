package com.patchmgmt.repository;

import com.patchmgmt.entity.MaintenanceWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindow, Long> {
    List<MaintenanceWindow> findByActiveTrue();
    List<MaintenanceWindow> findByEnvironmentAndActiveTrue(String environment);

    @Query("SELECT mw FROM MaintenanceWindow mw WHERE mw.active = true " +
           "AND (mw.environment IS NULL OR mw.environment = '' OR mw.environment = :env) " +
           "AND mw.startTime <= :currentTime AND mw.endTime >= :currentTime " +
           "AND (mw.dayOfWeek IS NULL OR mw.dayOfWeek = :dow)")
    List<MaintenanceWindow> findActiveWindowsForNow(
        @Param("env") String environment,
        @Param("currentTime") LocalTime currentTime,
        @Param("dow") DayOfWeek dayOfWeek
    );
}
