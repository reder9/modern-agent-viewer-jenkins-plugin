# AGENTS.md — Jenkins Modern Agent Viewer Plugin (`modern-agent-viewer`)

## Overview
- **Plugin ID**: `modern-agent-viewer`
- **Repository**: `reder9/modern-agent-viewer-jenkins-plugin` (`jenkinsci/modern-agent-viewer-plugin`)
- **Java Package**: `io.jenkins.plugins.fleetlens`
- **Jenkins Baseline**: `2.555.x` (`org.jenkins-ci.plugins:plugin:6.2250.va_d01d559a_209`, Java 17 / 21+)

Provides a real-time fleet observability, telemetry, queue inspection, cloud provisioner detection, and agent management dashboard for Jenkins controllers and nodes.

---

## Build, Test & Verification Commands

### Prerequisites
- **JDK**: Java 21 (recommended) or Java 17+
- **Maven**: Apache Maven `3.9+`

### Core Maven Commands
```bash
# Full build, unit/integration tests, SpotBugs, and Enforcer checks (always run before opening a PR)
mvn clean verify

# Run unit tests quickly (skipping slow InjectedTest Jelly scan during development)
mvn test -Dtest=!InjectedTest

# Run a specific test class
mvn test -Dtest=FleetLensActionTest
mvn test -Dtest=AgentProvisionerDetectorTest

# Run SpotBugs and Maven Enforcer checks directly
mvn spotbugs:check enforcer:enforce

# Package the .hpi plugin artifact without running tests
mvn clean package -DskipTests

# Start a local Jenkins instance at http://localhost:8080/jenkins
mvn hpi:run
```

---

## Architecture & Source Layout

- `src/main/java/io/jenkins/plugins/fleetlens/`
  - `FleetLensAction.java`, `FleetLensComputerAction.java`, `FleetLensComputerSetAction.java`, `FleetRedirectAction.java`, `FleetLensPageDecorator.java` — Stapler actions and UI entry points for the global fleet dashboard and individual node views.
  - `FleetLensDataService.java` & `FleetJsonSerializer.java` — Aggregates fleet telemetry, executor utilization, JVM version/clock-drift diagnostics, and queue metrics into JSON DTOs.
  - `config/FleetLensGlobalConfiguration.java` — Global configuration (`replaceDefaultComputerView`, refresh intervals, thresholds).
  - `model/` — Immutable DTOs (`FleetOverviewDto`, `AgentDto`, `ConnectionDto`, `ExecutorSlotDto`, `JvmDto`, `MemoryDto`, `OsDto`, `ProvisionerDto`, `QueueOverviewDto`, `StorageDto`, `TimeMetricsDto`).
  - `provisioner/` — Cloud and node provisioner detectors (`AwsEc2Detector`, `AzureDetector`, `DockerDetector`, `GcpDetector`, `KubernetesDetector`, `NomadDetector`, `OpenStackDetector`, `ProxmoxDetector`, `SwarmDetector`, `VSphereDetector`, `StaticNodeDetector`, `BuiltInNodeDetector`).
  - `telemetry/` & `telemetry/callable/` — Non-blocking background telemetry executor (`FleetLensTelemetryExecutor`, `FleetLensTelemetryRefresher`, `FleetLensComputerListener`) and `MasterToSlaveCallable` probes (`FetchOsInfoCallable`, `FetchStorageCallable`, `FetchHostUptimeCallable`, `FetchAgentNetworkCallable`, `ExecuteDiagnosticCommandCallable`).
  - `queue/QueueInspectionService.java` — Memoized build queue inspection and blockage reason analysis.
- `src/main/resources/io/jenkins/plugins/fleetlens/` & `src/main/webapp/`
  - Jelly views, CSS custom property stylesheets, and CSP-compliant frontend scripts.
- `src/test/java/io/jenkins/plugins/fleetlens/`
  - `FleetLensActionTest.java`, `FleetLensGlobalConfigurationTest.java`, `AgentProvisionerDetectorTest.java`, `AgentOsDetectorTest.java`, `AgentStorageAndUptimeTest.java`, `QueueInspectionServiceTest.java`, `telemetry/*Test.java`.

---

## Coding, Security & UI Guardrails

1. **Endpoint Security & Permissions**:
   - Annotate all state-mutating Stapler endpoints (`doLaunchAgent`, `doToggleOffline`, `doRunDiagnostic`, `doCheck*`, etc.) with `@RequirePOST` / `@POST`.
   - Always enforce appropriate Jenkins permissions (`Computer.CONNECT`, `Computer.DISCONNECT`, `Jenkins.ADMINISTER`, `Computer.EXTENDED_READ`) before returning sensitive telemetry or mutating node state.
2. **Non-Blocking Telemetry over Remoting**:
   - Never perform synchronous Remoting calls (`VirtualChannel.call(...)`) on HTTP request threads. All remote agent probes must execute asynchronously via `FleetLensTelemetryExecutor` with bounded timeouts and cached results.
3. **Jenkins CSP & Theme Compliance**:
   - Strictly use theme-aware CSS custom properties (`--fl-*` mapped to Jenkins theme variables) across all declarations.
   - Never use raw emojis in UI templates, badges, or Java strings; use dedicated SVG icons.
   - Never call native browser `alert()`, `confirm()`, or `prompt()`; use custom modal dialogs and toast notifications.
