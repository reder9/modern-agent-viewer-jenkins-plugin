package io.jenkins.plugins.fleetlens.api.dto;

import java.io.Serializable;

/**
 * Data Transfer Object representing dual-clock metrics: Jenkins connection duration vs host OS boot uptime.
 */
public class TimeMetricsDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean connected;
    private long connectTimestamp = -1;
    private long connectedDurationMs = -1;
    private String connectedDurationHuman = "--";

    private long hostUptimeMs = -1;
    private String hostUptimeHuman = "--";
    private long bootTimestamp = -1;

    public TimeMetricsDto() {}

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public long getConnectTimestamp() {
        return connectTimestamp;
    }

    public void setConnectTimestamp(long connectTimestamp) {
        this.connectTimestamp = connectTimestamp;
    }

    public long getConnectedDurationMs() {
        return connectedDurationMs;
    }

    public void setConnectedDurationMs(long connectedDurationMs) {
        this.connectedDurationMs = connectedDurationMs;
    }

    public String getConnectedDurationHuman() {
        return connectedDurationHuman;
    }

    public void setConnectedDurationHuman(String connectedDurationHuman) {
        this.connectedDurationHuman = connectedDurationHuman;
    }

    public long getHostUptimeMs() {
        return hostUptimeMs;
    }

    public void setHostUptimeMs(long hostUptimeMs) {
        this.hostUptimeMs = hostUptimeMs;
    }

    public String getHostUptimeHuman() {
        return hostUptimeHuman;
    }

    public void setHostUptimeHuman(String hostUptimeHuman) {
        this.hostUptimeHuman = hostUptimeHuman;
    }

    public long getBootTimestamp() {
        return bootTimestamp;
    }

    public void setBootTimestamp(long bootTimestamp) {
        this.bootTimestamp = bootTimestamp;
    }
}
