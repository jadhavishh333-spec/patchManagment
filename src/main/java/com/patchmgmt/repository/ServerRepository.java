package com.patchmgmt.repository;

import com.patchmgmt.entity.Server;
import com.patchmgmt.enums.ApprovalStatus;
import com.patchmgmt.enums.ComplianceStatus;
import com.patchmgmt.enums.OsType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ServerRepository extends JpaRepository<Server, Long> {
    @Query("SELECT s FROM Server s WHERE s.active = true AND (s.approvalStatus = 'APPROVED' OR s.approvalStatus IS NULL)")
    List<Server> findByActiveTrue();

    @Query("SELECT COUNT(s) FROM Server s WHERE s.active = true AND (s.approvalStatus = 'APPROVED' OR s.approvalStatus IS NULL)")
    long countByActiveTrue();

    List<Server> findByApprovalStatus(ApprovalStatus status);
    long countByOsType(OsType osType);
    List<Server> findByOsType(OsType osType);
    Optional<Server> findByIpAddress(String ipAddress);
    List<Server> findByEnvironment(String environment);
    List<Server> findByBusinessUnit(String businessUnit);
    List<Server> findByComplianceStatus(ComplianceStatus status);
    long countByEnvironment(String environment);
    boolean existsByIpAddress(String ipAddress);

    @Query("SELECT DISTINCT s.environment FROM Server s WHERE s.environment IS NOT NULL ORDER BY s.environment")
    List<String> findDistinctEnvironments();

    @Query("SELECT DISTINCT s.businessUnit FROM Server s WHERE s.businessUnit IS NOT NULL ORDER BY s.businessUnit")
    List<String> findDistinctBusinessUnits();

    @Query("SELECT COUNT(s) FROM Server s WHERE s.environment = :env AND s.active = true")
    long countActiveByEnvironment(String env);
}
