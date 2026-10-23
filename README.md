# Modern Agent Viewer Jenkins Plugin

Modern Agent Viewer transforms the default Jenkins agent and computer management interface into a real-time, high-density operations console.

---

## Key Features

### 1. Dynamic Provider Categorization
Automatically inspects runtime characteristics, cloud descriptors, and class hierarchies to categorize and filter machines by their provisioner:
- Docker Containers (Docker Cloud / Docker plugin) with container IDs and images.
- Kubernetes Pods (Kubernetes plugin) with pod names and namespaces.
- AWS EC2 (Amazon EC2 plugin) with instance types and IDs.
- Azure VMs (Azure VM Agents) with VM sizes and resource groups.
- Google Cloud Platform (GCP) (GCE plugin) with machine types and zones.
- Static / Dedicated Nodes (Permanent bare-metal / VMs via SSH, Inbound, WebSocket).
- Controller (Built-in) (Jenkins controller node with JVM details).

> **Graceful Reflection:** All cloud detectors use optional reflection. If your Jenkins instance does not have the AWS EC2 or Kubernetes plugins installed, Modern Agent Viewer compiles, loads, and runs cleanly with zero missing dependency errors.

---

### 2. Deep OS and CPU Architecture Telemetry
- Linux Distro Probing: Reads `/etc/os-release` dynamically on the agent to display exact distributions (e.g., Ubuntu 24.04 LTS, Alpine Linux, Debian 12, Amazon Linux 2023, RHEL 9).
- Windows Versioning: Distinguishes between Windows Server 2025/2022/2019/2016 and Windows 10/11.
- macOS Identification: Displays Darwin and macOS release names (e.g., macOS 15 Sequoia, macOS 14 Sonoma) and identifies Apple Silicon (`arm64`) vs Intel (`x86_64`).
- Architecture Badges: Visible chips for `amd64`, `arm64`, `s390x`, `x86`.

---

### 3. Visual Disk Space and Storage Monitoring
- Live Disk Gauges: Clear percentage bars showing used and total storage for the agent remote workspace filesystem root.
- Color-Coded Health Thresholds:
  - Healthy: less than 78%
  - Warning: 78% to 90% (or less than 5 GB free)
  - Critical Alert: greater than 90% (or less than 2 GB free)
- Temporary Storage Tracking: Separate monitoring for `/tmp` or `%TEMP%` directory space.
- Smart Caching: Background cached sampling to prevent disk I/O saturation on busy build agents.

---

### 4. Dual-Clock Telemetry: Connection Time vs. Host OS Uptime
- Session Connected (`connectedDuration`): Tracks how long the remoting channel has stayed continuously connected to Jenkins. Quickly flags network flapping if a permanent node shows a connection time of only 15 minutes.
- Host OS Uptime (`hostUptime`): Probed via `/proc/uptime` or system metrics. Identifies servers that have been running for 100+ days and require reboots or OS patches.

---

### 5. Modern UI/UX Experience
- Visual Card Matrix: Clean card layout with gauges, provider badges, executor status chips, and quick actions.
- High-Density Table View: Fast, sortable data table across all metrics (name, provider, OS, status, storage, uptime, connection duration, executors).
- Instant Client-Side Search: Search across machine names, labels, hostnames, IP addresses, OS distros, and cloud instance IDs.
- Live Summary KPIs: Total machines, online capacity, active workload, idle nodes, offline count, and storage warning alerts.
- Auto-Refresh: Configurable polling (5s, 15s, 30s, 60s, paused) with zero full-page reload flicker.

---

### 6. Interactive Diagnostic Console & Node Management
- Safe Diagnostic Console: Run read-only, audited diagnostics directly over the Jenkins Remoting Channel (e.g., `df -h`, `free -m`, `uname -a`, `Get-ComputerInfo`, `Get-Process`).
- Clustered Action Bar: Grouped actions for Tools (SSH/CLI Connect Helper, Diagnostic Console), Management (Take Offline / Bring Online), and Navigation (Refresh, Full View).
- Universal Copy to Clipboard: 1-click clipboard copy for IP addresses, node names, and diagnostic output.
- Backwards-Compatible Routing: Primary route `/agents`, with legacy `/fleet` redirect and classic `/computer/` transition banner.

---

### 7. Fleet Inventory Export (CSV & JSON)
- Client-Side Filter-Aware Export: 1-click export of current filtered/sorted agent views to CSV or structured JSON.
- Telemetry Included: Machine name, status, cloud provider, OS and architecture, IP address, hostname, used/total storage, uptime, connected session duration, active/total executors, and labels.
- Automated API Endpoint: Direct GET `/agents/exportCsv` endpoint for external scripts, audit pipelines, and metrics aggregators.

---

### 8. Bulk Fleet Operations & Floating Action Bar
- Multi-Select Support: Checkboxes on both Card Matrix and High-Density Table headers and rows.
- Floating Action Bar: Dynamically appears when one or more agents are selected:
  - Take Selected Offline: Bulk mark selected agents for maintenance with a single confirmation modal and reason.
  - Bring Selected Online: Bulk return selected agents to service.
  - Copy Selected IPs: Copy newline-delimited IP addresses of all selected agents to the clipboard.
  - Deselect All: One-click clear of active fleet selection.
- One-Click Launch / Reconnect: Direct action button to trigger slave agent launch directly from card and table toolbars for disconnected nodes.

---

### 9. Jenkins Internationalization (i18n)
- Complies with Jenkins core localization standards via `Messages.properties`.
- Ready for community translations into multiple languages.

---

## Configuration as Code (JCasC)

```yaml
unclassified:
  fleetLens:
    defaultViewMode: "cards"
    redirectComputerSet: false
    storageWarningPercent: 78
    storageCriticalPercent: 90
    autoRefreshSeconds: 15
```

---

## Building and Testing

```bash
# Compile and run all unit and integration tests
mvn clean test

# Package HPI plugin file
mvn clean package -DskipTests
```
