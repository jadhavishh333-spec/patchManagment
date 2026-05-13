package com.patchmgmt.config;

import com.patchmgmt.entity.*;
import com.patchmgmt.enums.*;
import com.patchmgmt.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ServerRepository serverRepository;
    private final PatchRepository patchRepository;
    private final MaintenanceWindowRepository maintenanceWindowRepository;
    private final EnvironmentRepository environmentRepository;
    private final ComplianceRecordRepository complianceRecordRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedUsers();
        seedEnvironments();
        seedServers();
        seedPatches();
        seedMaintenanceWindows();
        seedComplianceRecords();
    }

    private void seedUsers() {
        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(AppUser.builder()
                .username("admin").email("admin@patchmgmt.com")
                .password(passwordEncoder.encode("Admin@123"))
                .fullName("System Administrator").role(UserRole.ROLE_ADMIN).enabled(true).build());
            log.info("Admin user created: admin / Admin@123");
        }
        if (!userRepository.existsByUsername("user1")) {
            userRepository.save(AppUser.builder()
                .username("user1").email("user1@patchmgmt.com")
                .password(passwordEncoder.encode("User@123"))
                .fullName("John Doe").role(UserRole.ROLE_USER).enabled(true).build());
            log.info("Regular user created: user1 / User@123");
        }
        if (!userRepository.existsByUsername("devops")) {
            userRepository.save(AppUser.builder()
                .username("devops").email("devops@patchmgmt.com")
                .password(passwordEncoder.encode("DevOps@123"))
                .fullName("DevOps Engineer").role(UserRole.ROLE_USER).enabled(true).build());
        }
    }

    private void seedEnvironments() {
        if (environmentRepository.count() == 0) {
            environmentRepository.save(Environment.builder().name("PROD").description("Production environment")
                .businessUnit("Core Banking").owner("ops-team@company.com").criticality("CRITICAL").build());
            environmentRepository.save(Environment.builder().name("STAGING").description("Staging / Pre-prod")
                .businessUnit("Core Banking").owner("qa-team@company.com").criticality("HIGH").build());
            environmentRepository.save(Environment.builder().name("DEV").description("Development environment")
                .businessUnit("Engineering").owner("dev-team@company.com").criticality("MEDIUM").build());
            environmentRepository.save(Environment.builder().name("UAT").description("User Acceptance Testing")
                .businessUnit("QA").owner("uat-team@company.com").criticality("MEDIUM").build());
            log.info("Sample environments created");
        }
    }

    private void seedServers() {
        if (serverRepository.count() == 0) {
            AppUser admin = userRepository.findByUsername("admin").orElse(null);
            serverRepository.save(Server.builder().name("WEB-PROD-01").ipAddress("192.168.1.10")
                .osType(OsType.WINDOWS).osVersion("Windows Server 2022").environment("PROD")
                .businessUnit("Core Banking").description("Primary web server — Production")
                .active(true).winrmPort(5985).sshPort(22)
                .executionStrategy(ExecutionStrategyType.MOCK)
                .complianceStatus(ComplianceStatus.COMPLIANT)
                .createdBy(admin).build());
            serverRepository.save(Server.builder().name("DB-PROD-01").ipAddress("192.168.1.20")
                .osType(OsType.LINUX).osVersion("Ubuntu 22.04 LTS").environment("PROD")
                .businessUnit("Core Banking").description("Primary PostgreSQL database server")
                .active(true).sshPort(22)
                .executionStrategy(ExecutionStrategyType.MOCK)
                .complianceStatus(ComplianceStatus.COMPLIANT)
                .createdBy(admin).build());
            serverRepository.save(Server.builder().name("APP-DEV-01").ipAddress("192.168.2.10")
                .osType(OsType.LINUX).osVersion("CentOS Stream 9").environment("DEV")
                .businessUnit("Engineering").description("Dev application server")
                .active(true).sshPort(22)
                .executionStrategy(ExecutionStrategyType.MOCK)
                .complianceStatus(ComplianceStatus.NON_COMPLIANT)
                .createdBy(admin).build());
            serverRepository.save(Server.builder().name("WIN-STAGING-01").ipAddress("192.168.3.10")
                .osType(OsType.WINDOWS).osVersion("Windows Server 2019").environment("STAGING")
                .businessUnit("QA").description("Staging windows server")
                .active(true).winrmPort(5985)
                .executionStrategy(ExecutionStrategyType.MOCK)
                .complianceStatus(ComplianceStatus.UNKNOWN)
                .createdBy(admin).build());
            serverRepository.save(Server.builder().name("LNX-PROD-02").ipAddress("192.168.1.30")
                .osType(OsType.LINUX).osVersion("Red Hat Enterprise Linux 9").environment("PROD")
                .businessUnit("Core Banking").description("Secondary Linux production server")
                .active(true).sshPort(22)
                .executionStrategy(ExecutionStrategyType.MOCK)
                .complianceStatus(ComplianceStatus.COMPLIANT)
                .createdBy(admin).build());
            log.info("Sample servers created");
        }
    }

    private void seedPatches() {
        if (patchRepository.count() == 0) {
            AppUser admin = userRepository.findByUsername("admin").orElse(null);
            patchRepository.save(Patch.builder().title("Security Update KB5031356").patchId("KB5031356")
                .description("Critical security update for Windows Server addressing remote code execution vulnerability.")
                .osType(OsType.WINDOWS).severity(PatchSeverity.CRITICAL).requiresReboot(true)
                .releaseDate(LocalDateTime.now().minusDays(5))
                .installCommand("wusa.exe /install KB5031356.msu /quiet /norestart").createdBy(admin).build());
            patchRepository.save(Patch.builder().title("Cumulative Update KB5030219").patchId("KB5030219")
                .description("Monthly cumulative update for Windows Server 2022 with security fixes.")
                .osType(OsType.WINDOWS).severity(PatchSeverity.HIGH).requiresReboot(true)
                .releaseDate(LocalDateTime.now().minusDays(10))
                .installCommand("wusa.exe /install KB5030219.msu /quiet /norestart").createdBy(admin).build());
            patchRepository.save(Patch.builder().title("OpenSSL CVE-2023-0215 Fix").patchId("CVE-2023-0215")
                .description("Fixes use-after-free vulnerability in OpenSSL BIO_new_NDEF.")
                .osType(OsType.LINUX).severity(PatchSeverity.HIGH).requiresReboot(false)
                .releaseDate(LocalDateTime.now().minusDays(3))
                .installCommand("apt-get update && apt-get install -y openssl").createdBy(admin).build());
            patchRepository.save(Patch.builder().title("Linux Kernel Security Patch 6.1.57").patchId("KERNEL-6.1.57")
                .description("Linux kernel update fixing privilege escalation vulnerability.")
                .osType(OsType.LINUX).severity(PatchSeverity.CRITICAL).requiresReboot(true)
                .releaseDate(LocalDateTime.now().minusDays(7))
                .installCommand("apt-get update && apt-get install -y linux-image-generic && update-grub").createdBy(admin).build());
            patchRepository.save(Patch.builder().title("curl Security Update 8.4.0").patchId("CVE-2023-38545")
                .description("SOCKS5 heap buffer overflow vulnerability fix in curl.")
                .osType(OsType.LINUX).severity(PatchSeverity.MEDIUM).requiresReboot(false)
                .releaseDate(LocalDateTime.now().minusDays(2))
                .installCommand("apt-get update && apt-get install -y curl").createdBy(admin).build());
            patchRepository.save(Patch.builder().title("Windows Defender Signature Update").patchId("WD-2024-001")
                .description("Latest Windows Defender signature update with improved detection rules.")
                .osType(OsType.WINDOWS).severity(PatchSeverity.MEDIUM).requiresReboot(false)
                .releaseDate(LocalDateTime.now().minusDays(1))
                .installCommand("Update-MpSignature -UpdateSource MicrosoftUpdateServer").createdBy(admin).build());
            log.info("Sample patches created");
        }
    }

    private void seedMaintenanceWindows() {
        if (maintenanceWindowRepository.count() == 0) {
            AppUser admin = userRepository.findByUsername("admin").orElse(null);
            maintenanceWindowRepository.save(MaintenanceWindow.builder()
                .name("PROD Weekly Maintenance").description("Saturdays 02:00–06:00 for PROD servers")
                .windowType(WindowType.WEEKLY).startTime(LocalTime.of(2, 0)).endTime(LocalTime.of(6, 0))
                .dayOfWeek(DayOfWeek.SATURDAY).environment("PROD").active(true).createdBy(admin).build());
            maintenanceWindowRepository.save(MaintenanceWindow.builder()
                .name("DEV Daily Window").description("DEV servers — daily 22:00–23:59 window")
                .windowType(WindowType.DAILY).startTime(LocalTime.of(22, 0)).endTime(LocalTime.of(23, 59))
                .environment("DEV").active(true).createdBy(admin).build());
            maintenanceWindowRepository.save(MaintenanceWindow.builder()
                .name("Q1 2026 Emergency Window").description("One-time emergency patching window")
                .windowType(WindowType.ONE_TIME)
                .startTime(LocalTime.of(0, 0)).endTime(LocalTime.of(23, 59))
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(7))
                .environment("STAGING").active(true).createdBy(admin).build());
            log.info("Sample maintenance windows created");
        }
    }

    private void seedComplianceRecords() {
        if (complianceRecordRepository.count() == 0) {
            serverRepository.findAll().forEach(server -> {
                patchRepository.findAll().stream()
                    .filter(p -> p.getOsType() == server.getOsType())
                    .limit(2)
                    .forEach(patch -> {
                        ComplianceStatus status = server.getComplianceStatus() != null
                            ? server.getComplianceStatus() : ComplianceStatus.UNKNOWN;
                        complianceRecordRepository.save(ComplianceRecord.builder()
                            .server(server).patch(patch).status(status)
                            .complianceDate(LocalDate.now().minusDays((long)(Math.random() * 30)))
                            .verifiedBy("SYSTEM").notes("Auto-seeded for demo").build());
                    });
            });
            log.info("Sample compliance records seeded");
        }
    }
}
