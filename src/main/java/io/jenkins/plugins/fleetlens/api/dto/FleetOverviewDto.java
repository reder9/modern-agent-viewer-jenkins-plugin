package io.jenkins.plugins.fleetlens.api.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * High-level fleet overview statistics and counts.
 */
public class FleetOverviewDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private int totalAgents;
    private int onlineAgents;
    private int offlineAgents;
    private int busyAgents;
    private int idleAgents;
    private int lowStorageAgents;

    private int totalExecutors;
    private int busyExecutors;
    private int idleExecutors;

    private Map<String, Integer> provisionerCounts = new LinkedHashMap<>();
    private Map<String, Integer> osCounts = new LinkedHashMap<>();
    private Map<String, Integer> statusCounts = new LinkedHashMap<>();
    private List<String> configuredClouds = new ArrayList<>();

    public FleetOverviewDto() {}

    public int getTotalAgents() {
        return totalAgents;
    }

    public void setTotalAgents(int totalAgents) {
        this.totalAgents = totalAgents;
    }

    public int getOnlineAgents() {
        return onlineAgents;
    }

    public void setOnlineAgents(int onlineAgents) {
        this.onlineAgents = onlineAgents;
    }

    public int getOfflineAgents() {
        return offlineAgents;
    }

    public void setOfflineAgents(int offlineAgents) {
        this.offlineAgents = offlineAgents;
    }

    public int getBusyAgents() {
        return busyAgents;
    }

    public void setBusyAgents(int busyAgents) {
        this.busyAgents = busyAgents;
    }

    public int getIdleAgents() {
        return idleAgents;
    }

    public void setIdleAgents(int idleAgents) {
        this.idleAgents = idleAgents;
    }

    public int getLowStorageAgents() {
        return lowStorageAgents;
    }

    public void setLowStorageAgents(int lowStorageAgents) {
        this.lowStorageAgents = lowStorageAgents;
    }

    public int getTotalExecutors() {
        return totalExecutors;
    }

    public void setTotalExecutors(int totalExecutors) {
        this.totalExecutors = totalExecutors;
    }

    public int getBusyExecutors() {
        return busyExecutors;
    }

    public void setBusyExecutors(int busyExecutors) {
        this.busyExecutors = busyExecutors;
    }

    public int getIdleExecutors() {
        return idleExecutors;
    }

    public void setIdleExecutors(int idleExecutors) {
        this.idleExecutors = idleExecutors;
    }

    public Map<String, Integer> getProvisionerCounts() {
        return Collections.unmodifiableMap(provisionerCounts);
    }

    public void setProvisionerCounts(Map<String, Integer> provisionerCounts) {
        this.provisionerCounts = provisionerCounts != null ? new LinkedHashMap<>(provisionerCounts) : new LinkedHashMap<>();
    }

    public void incrementProvisioner(String provisioner) {
        if (provisioner != null) {
            provisionerCounts.put(provisioner, provisionerCounts.getOrDefault(provisioner, 0) + 1);
        }
    }

    public Map<String, Integer> getOsCounts() {
        return Collections.unmodifiableMap(osCounts);
    }

    public void setOsCounts(Map<String, Integer> osCounts) {
        this.osCounts = osCounts != null ? new LinkedHashMap<>(osCounts) : new LinkedHashMap<>();
    }

    public void incrementOs(String os) {
        if (os != null) {
            osCounts.put(os, osCounts.getOrDefault(os, 0) + 1);
        }
    }

    public Map<String, Integer> getStatusCounts() {
        return Collections.unmodifiableMap(statusCounts);
    }

    public void setStatusCounts(Map<String, Integer> statusCounts) {
        this.statusCounts = statusCounts != null ? new LinkedHashMap<>(statusCounts) : new LinkedHashMap<>();
    }

    public void incrementStatus(String status) {
        if (status != null) {
            statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
        }
    }

    public List<String> getConfiguredClouds() {
        return Collections.unmodifiableList(configuredClouds);
    }

    public void setConfiguredClouds(List<String> configuredClouds) {
        this.configuredClouds = configuredClouds != null ? new ArrayList<>(configuredClouds) : new ArrayList<>();
    }

    public void addConfiguredCloud(String cloudId) {
        if (cloudId != null && !configuredClouds.contains(cloudId)) {
            configuredClouds.add(cloudId);
        }
    }
}
