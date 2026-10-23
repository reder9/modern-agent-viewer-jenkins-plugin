package io.jenkins.plugins.fleetlens.api;

import hudson.model.Computer;
import hudson.model.User;
import io.jenkins.plugins.fleetlens.api.dto.AgentDto;
import io.jenkins.plugins.fleetlens.api.dto.ExecutorSlotDto;
import io.jenkins.plugins.fleetlens.api.dto.FleetOverviewDto;
import io.jenkins.plugins.fleetlens.api.dto.OsDto;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;
import io.jenkins.plugins.fleetlens.api.dto.QueueItemDto;
import io.jenkins.plugins.fleetlens.api.dto.QueueOverviewDto;
import io.jenkins.plugins.fleetlens.api.dto.StorageDto;
import io.jenkins.plugins.fleetlens.api.dto.TimeMetricsDto;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import java.util.List;
import java.util.Map;

/**
 * Utility to serialize FleetLens DTOs into JSON objects for Stapler HTTP responses.
 */
public class FleetJsonSerializer {

    public static JSONObject serializeOverview(FleetOverviewDto overview) {
        JSONObject json = new JSONObject();
        if (overview == null) return json;

        json.put("totalAgents", overview.getTotalAgents());
        json.put("onlineAgents", overview.getOnlineAgents());
        json.put("offlineAgents", overview.getOfflineAgents());
        json.put("busyAgents", overview.getBusyAgents());
        json.put("idleAgents", overview.getIdleAgents());
        json.put("lowStorageAgents", overview.getLowStorageAgents());
        json.put("totalExecutors", overview.getTotalExecutors());
        json.put("busyExecutors", overview.getBusyExecutors());
        json.put("idleExecutors", overview.getIdleExecutors());

        JSONObject provisioners = new JSONObject();
        for (Map.Entry<String, Integer> entry : overview.getProvisionerCounts().entrySet()) {
            provisioners.put(entry.getKey(), entry.getValue());
        }
        json.put("provisionerCounts", provisioners);

        JSONObject osCounts = new JSONObject();
        for (Map.Entry<String, Integer> entry : overview.getOsCounts().entrySet()) {
            osCounts.put(entry.getKey(), entry.getValue());
        }
        json.put("osCounts", osCounts);

        JSONObject statusCounts = new JSONObject();
        for (Map.Entry<String, Integer> entry : overview.getStatusCounts().entrySet()) {
            statusCounts.put(entry.getKey(), entry.getValue());
        }
        json.put("statusCounts", statusCounts);

        JSONArray clouds = new JSONArray();
        if (overview.getConfiguredClouds() != null) {
            clouds.addAll(overview.getConfiguredClouds());
        }
        json.put("configuredClouds", clouds);

        return json;
    }

    public static JSONArray serializeAgents(List<AgentDto> agents, boolean userCanConfigure) {
        JSONArray array = new JSONArray();
        if (agents != null) {
            for (AgentDto agent : agents) {
                array.add(serializeAgent(agent, userCanConfigure));
            }
        }
        return array;
    }

    public static JSONObject serializeAgent(AgentDto agent, boolean userCanConfigure) {
        JSONObject json = new JSONObject();
        if (agent == null) return json;

        json.put("name", agent.getName());
        json.put("displayName", agent.getDisplayName());
        json.put("urlName", agent.getUrlName());
        json.put("status", agent.getStatus());
        json.put("isOnline", agent.isOnline());
        json.put("isOffline", agent.isOffline());
        json.put("isIdle", agent.isIdle());
        json.put("isConnecting", agent.isConnecting());
        json.put("isTemporarilyOffline", agent.isTemporarilyOffline());
        json.put("offlineCause", agent.getOfflineCause() != null ? agent.getOfflineCause() : "");
        json.put("offlineCauseReason", agent.getOfflineCauseReason() != null ? agent.getOfflineCauseReason() : "");

        json.put("remoteFs", agent.getRemoteFs() != null ? agent.getRemoteFs() : "");
        json.put("hostName", agent.getHostName() != null ? agent.getHostName() : "");
        json.put("ipAddress", agent.getIpAddress() != null ? agent.getIpAddress() : "");
        json.put("numExecutors", agent.getNumExecutors());
        if (agent.getResponseTimeMs() != null) {
            json.put("responseTimeMs", agent.getResponseTimeMs());
        }
        if (agent.getClockDifferenceMs() != null) {
            json.put("clockDifferenceMs", agent.getClockDifferenceMs());
        }
        json.put("canConfigure", userCanConfigure);

        // Provisioner
        ProvisionerDto prov = agent.getProvisioner();
        JSONObject provJson = new JSONObject();
        provJson.put("id", prov.getId());
        provJson.put("displayName", prov.getDisplayName());
        provJson.put("icon", prov.getIcon());
        provJson.put("badgeClass", prov.getBadgeClass());
        JSONObject detailsJson = new JSONObject();
        for (Map.Entry<String, String> d : prov.getDetails().entrySet()) {
            detailsJson.put(d.getKey(), d.getValue());
        }
        provJson.put("details", detailsJson);
        json.put("provisioner", provJson);

        // OS
        OsDto os = agent.getOs();
        JSONObject osJson = new JSONObject();
        osJson.put("family", os.getFamily());
        osJson.put("name", os.getName());
        osJson.put("prettyName", os.getPrettyName());
        osJson.put("version", os.getVersion());
        osJson.put("arch", os.getArch());
        osJson.put("icon", os.getIcon());
        json.put("os", osJson);

        // Storage
        StorageDto storage = agent.getStorage();
        JSONObject storageJson = new JSONObject();
        storageJson.put("available", storage.isAvailable());
        storageJson.put("rootPath", storage.getRootPath() != null ? storage.getRootPath() : "");
        storageJson.put("totalBytes", storage.getTotalBytes());
        storageJson.put("usedBytes", storage.getUsedBytes());
        storageJson.put("freeBytes", storage.getFreeBytes());
        storageJson.put("percentUsed", storage.getPercentUsed());
        storageJson.put("healthStatus", storage.getHealthStatus());
        storageJson.put("totalHuman", storage.getTotalHuman());
        storageJson.put("usedHuman", storage.getUsedHuman());
        storageJson.put("freeHuman", storage.getFreeHuman());
        storageJson.put("tempAvailable", storage.isTempAvailable());
        storageJson.put("tempFreeBytes", storage.getTempFreeBytes());
        storageJson.put("tempFreeHuman", storage.getTempFreeHuman());
        json.put("storage", storageJson);

        // Time Metrics
        TimeMetricsDto tm = agent.getTimeMetrics();
        JSONObject tmJson = new JSONObject();
        tmJson.put("connected", tm.isConnected());
        tmJson.put("connectTimestamp", tm.getConnectTimestamp());
        tmJson.put("connectedDurationMs", tm.getConnectedDurationMs());
        tmJson.put("connectedDurationHuman", tm.getConnectedDurationHuman());
        tmJson.put("hostUptimeMs", tm.getHostUptimeMs());
        tmJson.put("hostUptimeHuman", tm.getHostUptimeHuman());
        tmJson.put("bootTimestamp", tm.getBootTimestamp());
        json.put("timeMetrics", tmJson);

        // Executors
        JSONArray execArray = new JSONArray();
        for (ExecutorSlotDto exec : agent.getExecutors()) {
            JSONObject ej = new JSONObject();
            ej.put("number", exec.getNumber());
            ej.put("busy", exec.isBusy());
            ej.put("currentExecutable", exec.getCurrentExecutable() != null ? exec.getCurrentExecutable() : "");
            ej.put("currentExecutableUrl", exec.getCurrentExecutableUrl() != null ? exec.getCurrentExecutableUrl() : "");
            ej.put("progress", exec.getProgress());
            ej.put("durationHuman", exec.getDurationHuman() != null ? exec.getDurationHuman() : "");
            execArray.add(ej);
        }
        json.put("executors", execArray);

        // Labels
        JSONArray labelsArray = new JSONArray();
        for (String label : agent.getLabels()) {
            labelsArray.add(label);
        }
        json.put("labels", labelsArray);

        return json;
    }

    public static JSONObject serializeQueue(QueueOverviewDto queue) {
        JSONObject json = new JSONObject();
        if (queue == null) {
            json.put("totalWaiting", 0);
            json.put("stuckCount", 0);
            json.put("blockedCount", 0);
            json.put("buildableCount", 0);
            json.put("items", new JSONArray());
            return json;
        }

        json.put("totalWaiting", queue.getTotalWaiting());
        json.put("stuckCount", queue.getStuckCount());
        json.put("blockedCount", queue.getBlockedCount());
        json.put("buildableCount", queue.getBuildableCount());

        JSONArray itemsArray = new JSONArray();
        for (QueueItemDto item : queue.getItems()) {
            JSONObject ij = new JSONObject();
            ij.put("id", item.getId());
            ij.put("name", item.getName() != null ? item.getName() : "");
            ij.put("url", item.getUrl() != null ? item.getUrl() : "");
            ij.put("why", item.getWhy() != null ? item.getWhy() : "");
            ij.put("assignedLabel", item.getAssignedLabel() != null ? item.getAssignedLabel() : "");
            ij.put("inQueueSince", item.getInQueueSince());
            ij.put("inQueueDurationMs", item.getInQueueDurationMs());
            ij.put("inQueueDurationHuman", item.getInQueueDurationHuman() != null ? item.getInQueueDurationHuman() : "");
            ij.put("blocked", item.isBlocked());
            ij.put("buildable", item.isBuildable());
            ij.put("stuck", item.isStuck());
            ij.put("canCancel", item.isCanCancel());

            ij.put("matchingNodesCount", item.getMatchingNodesCount());
            ij.put("onlineMatchingNodesCount", item.getOnlineMatchingNodesCount());
            ij.put("idleExecutorsCount", item.getIdleExecutorsCount());
            ij.put("busyExecutorsCount", item.getBusyExecutorsCount());

            JSONArray namesArray = new JSONArray();
            for (String n : item.getMatchingNodeNames()) {
                namesArray.add(n);
            }
            ij.put("matchingNodeNames", namesArray);

            ij.put("bottleneckReason", item.getBottleneckReason() != null ? item.getBottleneckReason() : "UNKNOWN");
            ij.put("bottleneckMessage", item.getBottleneckMessage() != null ? item.getBottleneckMessage() : "");

            itemsArray.add(ij);
        }
        json.put("items", itemsArray);

        return json;
    }
}
