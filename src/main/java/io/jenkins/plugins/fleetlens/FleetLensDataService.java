package io.jenkins.plugins.fleetlens;

import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.node_monitors.ClockMonitor;
import hudson.node_monitors.ResponseTimeMonitor;
import hudson.util.ClockDifference;
import io.jenkins.plugins.fleetlens.api.dto.AgentDto;
import io.jenkins.plugins.fleetlens.api.dto.ExecutorSlotDto;
import io.jenkins.plugins.fleetlens.api.dto.FleetOverviewDto;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;
import io.jenkins.plugins.fleetlens.detector.AgentProvisionerDetector;
import io.jenkins.plugins.fleetlens.monitoring.AgentOsDetector;
import io.jenkins.plugins.fleetlens.monitoring.AgentStorageService;
import io.jenkins.plugins.fleetlens.monitoring.AgentUptimeService;
import jenkins.model.Jenkins;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Aggregates live computer and node states into comprehensive FleetLens DTO models.
 */
public class FleetLensDataService {

    private static final Logger LOGGER = Logger.getLogger(FleetLensDataService.class.getName());

    public static List<AgentDto> getAllAgents() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return Collections.emptyList();
        }

        Computer[] computers = jenkins.getComputers();
        List<AgentDto> result = new ArrayList<>(computers.length);

        for (Computer computer : computers) {
            if (computer != null) {
                result.add(toAgentDto(computer));
            }
        }

        return result;
    }

    public static FleetOverviewDto getFleetOverview() {
        List<AgentDto> agents = getAllAgents();
        FleetOverviewDto overview = new FleetOverviewDto();

        int total = agents.size();
        int online = 0;
        int offline = 0;
        int busy = 0;
        int idle = 0;
        int lowStorage = 0;
        int totalExec = 0;
        int busyExec = 0;

        for (AgentDto agent : agents) {
            if (agent.isOnline()) {
                online++;
                if (agent.isIdle()) {
                    idle++;
                } else {
                    busy++;
                }
            } else {
                offline++;
            }

            if ("WARNING".equals(agent.getStorage().getHealthStatus()) || "CRITICAL".equals(agent.getStorage().getHealthStatus())) {
                lowStorage++;
            }

            totalExec += agent.getNumExecutors();
            for (ExecutorSlotDto exec : agent.getExecutors()) {
                if (exec.isBusy()) {
                    busyExec++;
                }
            }

            overview.incrementProvisioner(agent.getProvisioner().getId());
            overview.incrementOs(agent.getOs().getFamily());
            overview.incrementStatus(agent.getStatus());
        }

        try {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins != null && jenkins.clouds != null) {
                for (hudson.slaves.Cloud cloud : jenkins.clouds) {
                    if (cloud == null || cloud.name == null) continue;
                    String cName = cloud.name.toLowerCase(java.util.Locale.ROOT);
                    String cls = cloud.getClass().getName().toLowerCase(java.util.Locale.ROOT);
                    String all = cName + " " + cls;
                    if (all.contains("ec2") || all.contains("aws")) {
                        overview.addConfiguredCloud("aws-ec2");
                    } else if (all.contains("docker")) {
                        overview.addConfiguredCloud("docker");
                    } else if (all.contains("kubernetes") || all.contains("k8s")) {
                        overview.addConfiguredCloud("kubernetes");
                    } else if (all.contains("azure")) {
                        overview.addConfiguredCloud("azure");
                    } else if (all.contains("gcp") || all.contains("gce")) {
                        overview.addConfiguredCloud("gcp");
                    } else if (all.contains("proxmox")) {
                        overview.addConfiguredCloud("proxmox");
                    } else if (all.contains("vsphere")) {
                        overview.addConfiguredCloud("vsphere");
                    } else if (all.contains("openstack")) {
                        overview.addConfiguredCloud("openstack");
                    } else if (all.contains("nomad")) {
                        overview.addConfiguredCloud("nomad");
                    } else if (all.contains("swarm")) {
                        overview.addConfiguredCloud("swarm");
                    } else if (all.contains("oci") || all.contains("oracle")) {
                        overview.addConfiguredCloud("oci");
                    } else if (all.contains("digitalocean")) {
                        overview.addConfiguredCloud("digitalocean");
                    } else if (all.contains("hetzner")) {
                        overview.addConfiguredCloud("hetzner");
                    } else {
                        overview.addConfiguredCloud("cloud");
                    }
                }
            }
        } catch (Exception ignored) {
            // Jenkins or clouds not accessible
        }

        overview.setTotalAgents(total);
        overview.setOnlineAgents(online);
        overview.setOfflineAgents(offline);
        overview.setBusyAgents(busy);
        overview.setIdleAgents(idle);
        overview.setLowStorageAgents(lowStorage);
        overview.setTotalExecutors(totalExec);
        overview.setBusyExecutors(busyExec);
        overview.setIdleExecutors(Math.max(0, totalExec - busyExec));

        return overview;
    }

    public static AgentDto toAgentDto(Computer computer) {
        AgentDto dto = new AgentDto();
        Node node = computer.getNode();

        String rawName = computer.getName();
        String name = rawName.isEmpty() ? "(built-in)" : rawName;
        dto.setName(name);
        dto.setDisplayName(computer.getDisplayName());
        dto.setUrlName(computer.getUrl());

        boolean isOnline = computer.isOnline();
        boolean isConnecting = computer.isConnecting();
        boolean isTemporarilyOffline = computer.isTemporarilyOffline();
        boolean isIdle = computer.isIdle();

        dto.setOnline(isOnline);
        dto.setOffline(!isOnline);
        dto.setConnecting(isConnecting);
        dto.setTemporarilyOffline(isTemporarilyOffline);
        dto.setIdle(isIdle);

        if (isConnecting) {
            dto.setStatus("CONNECTING");
        } else if (isTemporarilyOffline) {
            dto.setStatus("TEMPORARILY_OFFLINE");
        } else if (!isOnline) {
            dto.setStatus("OFFLINE");
        } else if (!isIdle) {
            dto.setStatus("BUSY");
        } else {
            dto.setStatus("IDLE");
        }

        if (computer.getOfflineCause() != null) {
            dto.setOfflineCause(computer.getOfflineCause().toString());
        }
        dto.setOfflineCauseReason(computer.getOfflineCauseReason());

        // Provisioner
        ProvisionerDto provisioner = AgentProvisionerDetector.resolve(computer, node);
        dto.setProvisioner(provisioner);

        // OS
        dto.setOs(AgentOsDetector.detect(computer));

        // Storage
        dto.setStorage(AgentStorageService.getStorageMetrics(computer, node));

        // Time Metrics (Uptime & Connection)
        dto.setTimeMetrics(AgentUptimeService.getTimeMetrics(computer));

        // Host & Node details
        String host = null;
        try {
            host = computer.getHostName();
            dto.setHostName(host);
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.FINE, "Failed to resolve hostname for " + computer.getName(), e);
        }

        String ip = null;
        if (host != null && !host.trim().isEmpty()) {
            try {
                ip = java.net.InetAddress.getByName(host).getHostAddress();
            } catch (Exception ignored) {
                // Host resolution fallback
            }
        }
        dto.setIpAddress(ip != null ? ip : host);
        dto.setNumExecutors(computer.getNumExecutors());
        if (node != null) {
            hudson.FilePath rootPath = node.getRootPath();
            if (rootPath != null) {
                dto.setRemoteFs(rootPath.getRemote());
            }
        }

        // Labels
        if (node != null) {
            List<String> labelNames = node.getAssignedLabels()
                    .stream()
                    .map(Label::getName)
                    .filter(l -> !l.equalsIgnoreCase(name))
                    .sorted()
                    .collect(Collectors.toList());
            dto.setLabels(labelNames);
        }

        // Executors
        List<ExecutorSlotDto> executorDtos = new ArrayList<>();
        List<Executor> executors = computer.getExecutors();
        for (int i = 0; i < executors.size(); i++) {
            Executor e = executors.get(i);
            boolean execBusy = e.isBusy();
            String execName = null;
            String execUrl = null;
            int progress = -1;
            String duration = null;

            hudson.model.Queue.Executable executable = e.getCurrentExecutable();
            if (execBusy && executable != null) {
                execName = executable.toString();
                try {
                    if (executable instanceof hudson.model.Run) {
                        execUrl = ((hudson.model.Run<?, ?>) executable).getUrl();
                    } else {
                        java.lang.reflect.Method m = executable.getClass().getMethod("getUrl");
                        Object res = m.invoke(executable);
                        if (res != null) {
                            execUrl = String.valueOf(res);
                        }
                    }
                } catch (ReflectiveOperationException | SecurityException ex) {
                    LOGGER.log(Level.FINE, "Failed to resolve executable url", ex);
                }
                progress = e.getProgress();
                duration = AgentUptimeService.formatDuration(e.getElapsedTime());
            }

            executorDtos.add(new ExecutorSlotDto(i, execBusy, execName, execUrl, progress, duration));
        }
        dto.setExecutors(executorDtos);

        // Latency / Ping and Clock Skew via public getMonitorData()
        try {
            java.util.Map<String, Object> monitorData = computer.getMonitorData();
            if (monitorData != null) {
                Object rtmObj = monitorData.get("hudson.node_monitors.ResponseTimeMonitor");
                if (rtmObj instanceof ResponseTimeMonitor.Data) {
                    dto.setResponseTimeMs(((ResponseTimeMonitor.Data) rtmObj).getAverage());
                }

                Object clockObj = monitorData.get("hudson.node_monitors.ClockMonitor");
                if (clockObj instanceof ClockDifference) {
                    dto.setClockDifferenceMs((long) ((ClockDifference) clockObj).diff);
                }
            }
        } catch (RuntimeException ex) {
            LOGGER.log(Level.FINE, "Failed to read monitor data for " + computer.getName(), ex);
        }

        return dto;
    }
}
