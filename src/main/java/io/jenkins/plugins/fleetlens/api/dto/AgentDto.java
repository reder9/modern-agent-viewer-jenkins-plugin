package io.jenkins.plugins.fleetlens.api.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Complete snapshot of a Jenkins Agent / Node.
 */
public class AgentDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String displayName;
    private String urlName;
    private String status = "OFFLINE"; // ONLINE, OFFLINE, BUSY, IDLE, CONNECTING, TEMPORARILY_OFFLINE
    private boolean online;
    private boolean offline;
    private boolean idle;
    private boolean connecting;
    private boolean temporarilyOffline;
    private String offlineCause;
    private String offlineCauseReason;

    private ProvisionerDto provisioner = new ProvisionerDto("static", "Static Node", "symbol-server", "badge-static");
    private OsDto os = new OsDto("OTHER", "Unknown", "Unknown OS", "", "", "symbol-help");
    private StorageDto storage = new StorageDto();
    private TimeMetricsDto timeMetrics = new TimeMetricsDto();

    private List<ExecutorSlotDto> executors = new ArrayList<>();
    private List<String> labels = new ArrayList<>();
    private String remoteFs;
    private String hostName;
    private String ipAddress;
    private int numExecutors;
    private Long responseTimeMs;
    private Long clockDifferenceMs;

    public AgentDto() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getUrlName() {
        return urlName;
    }

    public void setUrlName(String urlName) {
        this.urlName = urlName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public boolean isOffline() {
        return offline;
    }

    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    public boolean isIdle() {
        return idle;
    }

    public void setIdle(boolean idle) {
        this.idle = idle;
    }

    public boolean isConnecting() {
        return connecting;
    }

    public void setConnecting(boolean connecting) {
        this.connecting = connecting;
    }

    public boolean isTemporarilyOffline() {
        return temporarilyOffline;
    }

    public void setTemporarilyOffline(boolean temporarilyOffline) {
        this.temporarilyOffline = temporarilyOffline;
    }

    public String getOfflineCause() {
        return offlineCause;
    }

    public void setOfflineCause(String offlineCause) {
        this.offlineCause = offlineCause;
    }

    public String getOfflineCauseReason() {
        return offlineCauseReason;
    }

    public void setOfflineCauseReason(String offlineCauseReason) {
        this.offlineCauseReason = offlineCauseReason;
    }

    public ProvisionerDto getProvisioner() {
        return provisioner;
    }

    public void setProvisioner(ProvisionerDto provisioner) {
        if (provisioner != null) {
            this.provisioner = provisioner;
        }
    }

    public OsDto getOs() {
        return os;
    }

    public void setOs(OsDto os) {
        if (os != null) {
            this.os = os;
        }
    }

    public StorageDto getStorage() {
        return storage;
    }

    public void setStorage(StorageDto storage) {
        if (storage != null) {
            this.storage = storage;
        }
    }

    public TimeMetricsDto getTimeMetrics() {
        return timeMetrics;
    }

    public void setTimeMetrics(TimeMetricsDto timeMetrics) {
        if (timeMetrics != null) {
            this.timeMetrics = timeMetrics;
        }
    }

    public List<ExecutorSlotDto> getExecutors() {
        return Collections.unmodifiableList(executors);
    }

    public void setExecutors(List<ExecutorSlotDto> executors) {
        this.executors = executors != null ? new ArrayList<>(executors) : new ArrayList<>();
    }

    public List<String> getLabels() {
        return Collections.unmodifiableList(labels);
    }

    public void setLabels(List<String> labels) {
        this.labels = labels != null ? new ArrayList<>(labels) : new ArrayList<>();
    }

    public String getRemoteFs() {
        return remoteFs;
    }

    public void setRemoteFs(String remoteFs) {
        this.remoteFs = remoteFs;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getNumExecutors() {
        return numExecutors;
    }

    public void setNumExecutors(int numExecutors) {
        this.numExecutors = numExecutors;
    }

    public Long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public Long getClockDifferenceMs() {
        return clockDifferenceMs;
    }

    public void setClockDifferenceMs(Long clockDifferenceMs) {
        this.clockDifferenceMs = clockDifferenceMs;
    }
}
