package com.patchmgmt.repository;

import com.patchmgmt.entity.ComplianceRecord;
import com.patchmgmt.enums.ComplianceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComplianceRecordRepository extends JpaRepository<ComplianceRecord, Long> {
    List<ComplianceRecord> findByServer_Id(Long serverId);
    List<ComplianceRecord> findByStatus(ComplianceStatus status);
    List<ComplianceRecord> findByServer_Environment(String environment);
    long countByStatus(ComplianceStatus status);
    long countByServer_Environment(String environment);
    long countByStatusAndServer_Environment(ComplianceStatus status, String environment);

    @Query("SELECT cr FROM ComplianceRecord cr WHERE cr.server.id = :serverId ORDER BY cr.createdAt DESC")
    List<ComplianceRecord> findLatestByServerId(@Param("serverId") Long serverId);

    @Query("SELECT DISTINCT cr.server.environment FROM ComplianceRecord cr WHERE cr.server.environment IS NOT NULL")
    List<String> findDistinctEnvironments();
}
