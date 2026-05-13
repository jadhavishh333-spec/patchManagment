# PatchOrchestrator — Enterprise Patch Management Platform

> **v2.0.0** | Spring Boot 3.2 · Java 21 · PostgreSQL · WinRM/SSH · CyberArk

An enterprise-grade automated OS patch orchestration platform supporting Windows and Linux servers with role-based access control, CyberArk credential management, maintenance window enforcement, compliance tracking, and rich reporting.

---

## ✨ Features

| Category | Capabilities |
|---|---|
| **Job Lifecycle** | Create → Approve → Schedule → Execute → Monitor → Retry |
| **Remote Execution** | WinRM (Windows) · SSH (Linux) · Mock (Demo/Dev) |
| **Credential Security** | CyberArk PAM integration — credentials fetched live, never stored |
| **Compliance** | Per-server and per-environment compliance tracking with history |
| **Maintenance Windows** | Scheduled / weekly / one-time windows that gate job execution |
| **Reporting** | CSV export (jobs, audit, execution) · PDF compliance report |
| **Dashboard** | Real-time KPIs, OS distribution chart, job status chart |
| **RBAC** | Admin (full control) · User (create & view jobs) |
| **Auditability** | Immutable audit log for all system actions |
| **Resilience** | Configurable retry logic, timeout enforcement, Spring Retry |

---

## 🚀 Quick Start (Demo Mode)

### Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 14+

### 1. Create the Database

```sql
CREATE DATABASE patch_management;
```

### 2. Configure `application.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/patch_management
    username: YOUR_DB_USER
    password: YOUR_DB_PASSWORD
```

### 3. Run

```bash
mvn spring-boot:run -DskipTests
```

Visit: **http://localhost:8080**

### 4. Login

| Role  | Username | Password   |
|-------|----------|------------|
| Admin | `admin`  | `Admin@123` |
| User  | `user1`  | `User@123`  |

> Click the credential chip on the login page to auto-fill.

> **Demo mode is ON by default** — no external infrastructure (WinRM, SSH, CyberArk) is needed.

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Browser / UI Layer                        │
│  Thymeleaf Templates · Custom Dark CSS · Chart.js · AJAX    │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTP
┌───────────────────────────▼─────────────────────────────────┐
│                 Spring MVC Controllers                        │
│  Dashboard · Jobs · Servers · Patches · Compliance           │
│  Maintenance · Reports · Admin · API (REST)                  │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                    Service Layer                              │
│  PatchJobService · ComplianceService · ReportService         │
│  MaintenanceWindowService · CredentialMappingService         │
└──────┬────────────────────┬────────────────────────┬─────────┘
       │                    │                        │
┌──────▼──────┐  ┌──────────▼─────────┐  ┌──────────▼──────────┐
│ Credential  │  │  Execution Engine   │  │   JPA / PostgreSQL   │
│  Provider   │  │                     │  │                      │
│ ─────────── │  │ WinRmStrategy       │  │ 10+ Repositories     │
│ CyberArk    │  │ SshStrategy         │  │ Server · PatchJob    │
│ (or Mock)   │  │ MockStrategy        │  │ ComplianceRecord     │
└─────────────┘  └─────────────────────┘  └──────────────────────┘
```

---

## 🔧 Configuration Reference

All configuration lives in `src/main/resources/application.yml`.

### Demo Mode (default: `true`)

```yaml
integration:
  demo-mode: true   # ← Set to false for production
```

When `demo-mode: true`:
- A **MockCredentialProvider** returns a simulated `root/password` credential
- A **MockExecutionStrategy** simulates command execution (sleep + fake output)
- No WinRM, SSH, or CyberArk connections are made

---

## 🔐 Switching to Production Mode

### Step 1: Disable Demo Mode

```yaml
integration:
  demo-mode: false
```

### Step 2: Configure CyberArk

```yaml
integration:
  cyberark:
    enabled: true
    base-url: https://your-cyberark-host/AIMWebService/api/Accounts
    app-id: PatchMgmtApp
    safe: PatchMgmtSafe
    connection-timeout-seconds: 10
    read-timeout-seconds: 15
    verify-ssl: true   # Set false only for self-signed certs in lab
```

**In CyberArk**: Create an Application with ID `PatchMgmtApp` and grant it access to the safe containing server credentials.

**In the app**: Go to **Admin → Credentials**, create a mapping for each server:
- Select the server
- Enter the CyberArk Safe name (e.g., `PatchMgmtSafe`)
- Enter the CyberArk Object name (e.g., `WEB-PROD-01-admin`)
- Optionally enter the CyberArk Username to filter by

During job execution, the platform calls the CyberArk AIM REST API to fetch credentials dynamically. Passwords are held in `char[]` and cleared immediately after use.

### Step 3: Enable WinRM (for Windows Servers)

```yaml
integration:
  winrm:
    enabled: true
    port: 5985          # 5986 for HTTPS
    use-https: false    # Set true for HTTPS
    timeout-seconds: 120
    auth-scheme: basic  # Options: basic | ntlm | kerberos
```

**On each Windows Server**, run:
```powershell
# Enable WinRM
Enable-PSRemoting -Force
Set-Item WSMan:\localhost\Service\Auth\Basic -Value $true
Set-Item WSMan:\localhost\Service\AllowUnencrypted -Value $true  # only for HTTP
netsh advfirewall firewall add rule name="WinRM-HTTP" dir=in localport=5985 protocol=TCP action=allow
```

**On each server entity**, set Execution Strategy to `WINRM`.

### Step 4: Enable SSH (for Linux Servers)

```yaml
integration:
  ssh:
    enabled: true
    port: 22
    timeout-seconds: 120
    strict-host-key-checking: false   # Set true in prod with known-hosts file
```

**On each Linux Server**:
- Ensure the patching user has `sudo` privileges (or runs as root)
- SSH service must be running and accessible from the application host
- Port 22 (or custom) must be open in the firewall

**On each server entity**, set Execution Strategy to `SSH`.

### Step 5: Set Server Execution Strategies

In the Server form (**Servers → Edit**), set:
- `WINRM` for Windows servers
- `SSH` for Linux servers

Or configure globally in the database seeds / DataInitializer.

---

## 🔄 Job Workflow

```
[Create Job] → PENDING
      ↓
[Admin Approves] → APPROVED
      ↓
[Scheduler or Manual Execute]
      ↓
[Maintenance Window Check] → ❌ BLOCKED (if outside window)
      ↓  ✅
[Credential Fetch] → CyberArk / Mock
      ↓
[Remote Command Execute] → WinRM / SSH / Mock  → IN_PROGRESS
      ↓
  ┌───┴────────────┐
  ↓                ↓
COMPLETED        FAILED
                  ↓
              [Retry if retryCount < maxRetries] → RETRYING
                  ↓
             [max retries exceeded] → FAILED permanently
      ↓ (always)
[Compliance Record Saved] → Server status updated
```

**Patch Commands** executed per OS:

| OS      | Command Template |
|---------|-----------------|
| Windows | `wusa.exe <patch.msu> /quiet /norestart` or custom `installCommand` |
| Linux   | `sudo apt-get install -y <package>` or `sudo yum install -y <package>` or custom |

---

## 📊 Reporting

| Report | Format | Endpoint |
|--------|--------|----------|
| Patch Job Report | CSV | `GET /reports/export/patch-csv?environment=PROD&status=COMPLETED` |
| Compliance Report | PDF | `GET /reports/export/compliance-pdf?environment=PROD` |
| Audit Log Export | CSV | `GET /reports/export/audit-csv` |
| Execution Log Export | CSV | `GET /reports/export/execution-csv` |

---

## 🔌 REST API

The platform exposes a REST API for integration with external tools:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/jobs` | GET | List all patch jobs |
| `/api/v1/jobs/{id}` | GET | Get job details |
| `/api/v1/jobs/{id}/status` | GET | Get job status (used for polling) |
| `/api/v1/jobs/{id}/execute` | POST | Trigger job execution |
| `/api/v1/servers` | GET | List servers |
| `/api/v1/compliance` | GET | Get compliance summary |
| `/actuator/health` | GET | Application health |

---

## 🛡 Security

| Area | Implementation |
|------|---------------|
| Authentication | Spring Security form login with BCrypt password hashing |
| Authorization | `ROLE_ADMIN` (full) / `ROLE_USER` (create + view) |
| CSRF | Enabled on all state-changing requests |
| Credentials | Stored as `char[]` and cleared after use — never logged |
| CyberArk | TLS-verified connection; App-ID based authentication |
| Session | 8-hour timeout; invalidated on logout |
| Actuator | Restricted to `ROLE_ADMIN` only |

---

## ⚙️ Patch Engine Settings

```yaml
patch:
  scheduler:
    enabled: true
    cron: "0 * * * * *"          # Every minute — check for due jobs
  execution:
    max-retry-attempts: 3
    retry-delay-seconds: 30
    batch-size: 5
    default-timeout-minutes: 30
    thread-pool-size: 10
```

Jobs execute asynchronously. Per-job retry and timeout can be overridden in the job's configuration.

---

## 🗂 Project Structure

```
src/main/java/com/patchmgmt/
├── config/               # AsyncConfig, Security, DataInitializer, Scheduler
├── controller/           # MVC + REST controllers
├── dto/                  # Data transfer objects
├── entity/               # JPA entities (10)
├── enums/                # PatchStatus, OsType, ComplianceStatus, …
├── exception/            # Custom exceptions
├── integration/
│   ├── credential/       # CyberArk + Mock credential providers
│   ├── execution/        # WinRM + SSH + Mock execution strategies
│   └── model/            # ExecutionResult, ResolvedCredential
├── repository/           # Spring Data JPA repositories (10)
└── service/              # Service interfaces + implementations (11)

src/main/resources/
├── application.yml       # All configuration
├── static/
│   ├── css/style.css     # Enterprise dark glassmorphism theme
│   └── js/app.js         # Chart.js, polling, live clock
└── templates/            # Thymeleaf templates (25+)
```

---

## 🧪 Data Seeded in Demo Mode

On first startup, the following sample data is created automatically:

- **2 users**: `admin` (ADMIN role), `user1` (USER role)
- **5 servers**: Mix of Windows and Linux across PROD/DEV/STAGING
- **6 patches**: Critical/High/Medium severity for both Windows and Linux
- **3 patch jobs**: In various states (PENDING, COMPLETED, FAILED)
- **3 maintenance windows**: Weekly PROD window, DEV daily window, One-time patch
- **4 environments**: PROD, DEV, STAGING, UAT
- **Compliance records**: Pre-seeded for all servers

---

## 🐛 Troubleshooting

| Issue | Fix |
|-------|-----|
| `500 on startup` | Check PostgreSQL is running and credentials in `application.yml` are correct |
| `CyberArk connection refused` | Verify `cyberark.base-url` and network access; check App-ID permissions |
| `WinRM authentication failure` | Ensure Basic auth is enabled on Windows target: `Set-Item WSMan:\localhost\Service\Auth\Basic -Value $true` |
| `SSH Permission denied` | Verify the CyberArk credential resolves to a user with SSH access |
| `Job stuck IN_PROGRESS` | Check async thread pool; increase `thread-pool-size` if under heavy load |
| `Compliance not updating` | Compliance is recorded after each job execution — run at least one job per server |

---

## 📄 License

Proprietary — Internal enterprise use only.

---

*Enterprise Patch Orchestration Platform · Built with Spring Boot 3.2 · Java 21*
