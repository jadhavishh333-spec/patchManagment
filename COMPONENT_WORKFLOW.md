# Application Component Workflow & Architecture

This document explains the internal working of different components in the PatchOrchestrator application.

## 1. Dashboard: How Data is Fetched
The Dashboard provides a real-time overview of the system state.
- **Frontend Layer**: Built using Thymeleaf templates (`index.html`) with embedded JavaScript (Chart.js) for visualization.
- **Controller Layer**: The `DashboardController` handles the `/dashboard` endpoint.
- **Service & Repository Layer**: The controller calls multiple services:
  - `ServerService.getServerStats()`: Queries the `ServerRepository` to count total servers, and groups them by OS type and Environment.
  - `PatchJobService.getJobStats()`: Queries the `PatchJobRepository` to get counts of jobs in different states (PENDING, APPROVED, IN_PROGRESS, COMPLETED, FAILED).
  - `ComplianceService.getOverallCompliance()`: Queries `ComplianceRecordRepository` to get the latest compliance status of the fleet.
- **Data Flow**: The aggregated data is added to the Spring MVC `Model` and rendered server-side by Thymeleaf. The rendered HTML is sent to the browser. Any live updates are fetched via AJAX calls to REST endpoints (`/api/v1/jobs/status`).

## 2. Patch Jobs: Creation, Storage, and Execution
Patch jobs are the core entities that track a patching operation.
- **Creation**:
  1. A user (or Admin) navigates to "Create Job" in the UI.
  2. They submit a form containing the Server ID, Patch ID, and Scheduled Time.
  3. The `PatchJobController` receives the HTTP POST request, validates it, and converts it to a `PatchJobDTO`.
  4. It calls `PatchJobService.createJob(dto)`.
- **Storage**:
  - The service instantiates a `PatchJob` JPA Entity.
  - It saves the entity using `PatchJobRepository.save()`.
  - The data is stored in the `patch_jobs` table in the PostgreSQL database. The initial status is set to `PENDING`.
- **Approval Workflow**:
  - `PENDING` jobs must be approved by a user with the `ROLE_ADMIN` role. Once approved, the status changes to `APPROVED`.
- **Execution**:
  - A background Spring `@Scheduled` task (in `JobScheduler`) runs every minute to find `APPROVED` jobs whose scheduled time has arrived.
  - Alternatively, jobs can be manually triggered via the UI ("Execute Now").

## 3. Remote Execution: SSH and WinRM
When a job executes, the application needs to connect to the target server. This is handled by the `integration/execution` module.
- **Execution Strategy Pattern**: The system uses the `RemoteExecutionStrategy` interface. Based on the target OS, a specific strategy is chosen:
  - **Windows (`WinRmExecutionStrategy`)**: Uses Java's `ProcessBuilder` to launch a PowerShell process on the host running the app. It executes an `Invoke-Command` cmdlet using WinRM to connect to the target Windows server. It authenticates using credentials fetched from CyberArk.
  - **Linux (`SshExecutionStrategy`)**: Uses the `Apache MINA SSHD` client library. It establishes a secure SSH connection to the target server on port 22, authenticates using CyberArk credentials, and executes the package manager command (e.g., `apt-get` or `yum`) in an exec channel.
- **Security**: Passwords are never stored in the database. During execution, the `CredentialMappingService` calls the `CyberArkCredentialProvider` (which hits the CyberArk AIM REST API) to fetch the credential dynamically. The password is kept in a `char[]` and cleared immediately after use.

## 4. Demo Mode vs. Real Mode
The application can run in two distinct modes, controlled by a single configuration flag in `application.yml`:

```yaml
integration:
  demo-mode: true  # Switch to 'false' for Real Mode
```

- **Demo Mode (`demo-mode: true`)**:
  - Designed for local development, testing, and demonstrations without needing external infrastructure.
  - **Credential Provider**: `MockCredentialProvider` is injected. It returns fake hardcoded credentials instead of calling CyberArk.
  - **Execution Strategy**: `MockExecutionStrategy` is injected. Instead of making real SSH/WinRM network connections, it uses `Thread.sleep()` to simulate execution delay and returns fake success logs.
  - **Data Initialization**: The `DataInitializer` bean automatically seeds the database with sample users, servers, patches, and historical jobs.

- **Real Mode (`demo-mode: false`)**:
  - Designed for Production.
  - **Credential Provider**: `CyberArkCredentialProvider` is injected. It makes live REST calls to your organization's CyberArk instance.
  - **Execution Strategy**: `WinRmExecutionStrategy` and `SshExecutionStrategy` are injected. They make real network connections to target servers and execute commands.

## 5. Maintenance Windows
To ensure patches are only applied during approved downtimes:
- When a job is triggered, the `MaintenanceWindowService` is consulted.
- It checks if the current time falls within any active maintenance window defined for the target server's environment.
- If no active window is found, the job execution is blocked.

## 6. Audit & Compliance
- **Audit Log**: Every significant action (Job Created, Job Approved, Job Executed, Status Changed) is recorded via the `AuditLogService` into the `audit_logs` table. This provides an immutable history.
- **Compliance**: After a patch job finishes (successfully or failed), the `ComplianceService` updates the `compliance_records` table. This tracking drives the Compliance Dashboard and PDF reports.
