package io.jenkins.plugins.fleetlens.queue;

import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import io.jenkins.plugins.fleetlens.api.dto.QueueItemDto;
import io.jenkins.plugins.fleetlens.api.dto.QueueOverviewDto;
import io.jenkins.plugins.fleetlens.monitoring.AgentUptimeService;
import jenkins.model.Jenkins;

import hudson.slaves.Cloud;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Inspects the Jenkins build queue and performs bottleneck analysis against connected fleet nodes.
 */
public class QueueInspectionService {

    private static final Logger LOGGER = Logger.getLogger(QueueInspectionService.class.getName());

    public static QueueOverviewDto inspectQueue() {
        QueueOverviewDto overview = new QueueOverviewDto();
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return overview;
        }

        Queue queue = jenkins.getQueue();
        Queue.Item[] items = queue.getItems();
        if (items == null || items.length == 0) {
            return overview;
        }

        List<QueueItemDto> itemDtos = new ArrayList<>(items.length);
        int stuck = 0;
        int blocked = 0;
        int buildable = 0;
        long now = System.currentTimeMillis();
        Map<String, LabelMatchSummary> labelSummaryMemo = new HashMap<>();

        for (Queue.Item item : items) {
            if (item == null) {
                continue;
            }

            QueueItemDto dto = new QueueItemDto();
            dto.setId(item.getId());
            dto.setName(item.task.getFullDisplayName());
            dto.setUrl(item.task.getUrl());
            dto.setWhy(item.getWhy());

            long inQueueSince = item.getInQueueSince();
            dto.setInQueueSince(inQueueSince);
            long durationMs = Math.max(0, now - inQueueSince);
            dto.setInQueueDurationMs(durationMs);
            dto.setInQueueDurationHuman(AgentUptimeService.formatDuration(durationMs));

            boolean isBlocked = item.isBlocked();
            boolean isBuildable = item.isBuildable();
            boolean isStuck = item.isStuck();

            dto.setBlocked(isBlocked);
            dto.setBuildable(isBuildable);
            dto.setStuck(isStuck);
            dto.setCanCancel(item.hasCancelPermission());

            if (isStuck) stuck++;
            if (isBlocked) blocked++;
            if (isBuildable) buildable++;

            // Label analysis with memoization for O(1) duplicate label lookups
            Label assignedLabel = item.getAssignedLabel();
            String labelStr = assignedLabel != null ? assignedLabel.getExpression() : null;
            dto.setAssignedLabel(labelStr);

            String memoKey = labelStr != null ? labelStr : "__ALL_NODES__";
            LabelMatchSummary summary = labelSummaryMemo.computeIfAbsent(memoKey, k -> computeLabelMatchSummary(jenkins, assignedLabel));
            applyLabelMatchSummary(summary, dto);

            itemDtos.add(dto);
        }

        overview.setTotalWaiting(itemDtos.size());
        overview.setStuckCount(stuck);
        overview.setBlockedCount(blocked);
        overview.setBuildableCount(buildable);
        overview.setItems(itemDtos);

        return overview;
    }

    private static class LabelMatchSummary {
        final int totalMatching;
        final int onlineCount;
        final int idleExec;
        final int busyExec;
        final List<String> nodeNames;
        final String provCloudName;

        LabelMatchSummary(int totalMatching, int onlineCount, int idleExec, int busyExec, List<String> nodeNames, String provCloudName) {
            this.totalMatching = totalMatching;
            this.onlineCount = onlineCount;
            this.idleExec = idleExec;
            this.busyExec = busyExec;
            this.nodeNames = nodeNames;
            this.provCloudName = provCloudName;
        }
    }

    private static LabelMatchSummary computeLabelMatchSummary(Jenkins jenkins, Label label) {
        Set<Node> candidateNodes;
        if (label != null) {
            candidateNodes = label.getNodes();
        } else {
            // Any normal mode node
            candidateNodes = new HashSet<>();
            for (Node n : jenkins.getNodes()) {
                if (n.getMode() == Node.Mode.NORMAL) {
                    candidateNodes.add(n);
                }
            }
            if (jenkins.getMode() == Node.Mode.NORMAL) {
                candidateNodes.add(jenkins);
            }
        }

        int totalMatching = candidateNodes.size();
        int onlineCount = 0;
        int idleExec = 0;
        int busyExec = 0;
        List<String> nodeNames = new ArrayList<>(totalMatching);

        for (Node node : candidateNodes) {
            String name = node.getNodeName().isEmpty() ? "(built-in)" : node.getNodeName();
            nodeNames.add(name);

            Computer computer = node.toComputer();
            if (computer != null && computer.isOnline() && !computer.isTemporarilyOffline()) {
                onlineCount++;
                idleExec += computer.countIdle();
                busyExec += computer.countBusy();
            }
        }

        Collections.sort(nodeNames);

        String provCloudName = null;
        if (totalMatching == 0 && label != null && jenkins.clouds != null) {
            for (Cloud cloud : jenkins.clouds) {
                try {
                    if (cloud.canProvision(label)) {
                        provCloudName = cloud.getDisplayName();
                        if (provCloudName == null || provCloudName.isEmpty()) {
                            provCloudName = cloud.name;
                        }
                        break;
                    }
                } catch (RuntimeException ignored) {
                    // ignore cloud check failure
                }
            }
        }

        return new LabelMatchSummary(totalMatching, onlineCount, idleExec, busyExec, nodeNames, provCloudName);
    }

    private static void applyLabelMatchSummary(LabelMatchSummary summary, QueueItemDto dto) {
        dto.setMatchingNodesCount(summary.totalMatching);
        dto.setOnlineMatchingNodesCount(summary.onlineCount);
        dto.setIdleExecutorsCount(summary.idleExec);
        dto.setBusyExecutorsCount(summary.busyExec);
        dto.setMatchingNodeNames(summary.nodeNames);

        // Determine bottleneck reason & human message
        String labelDisplay = dto.getAssignedLabel() != null ? "'" + dto.getAssignedLabel() + "'" : "any agent";

        if (summary.totalMatching == 0) {
            if (summary.provCloudName != null) {
                dto.setBottleneckReason("PROVISIONING_CLOUD");
                dto.setBottleneckMessage("Provisioning ephemeral machine via " + summary.provCloudName + " (" + labelDisplay + ")");
            } else {
                dto.setBottleneckReason("NO_MATCHING_NODES");
                dto.setBottleneckMessage("No machines configured with label " + labelDisplay);
            }
        } else if (summary.onlineCount == 0) {
            dto.setBottleneckReason("ALL_NODES_OFFLINE");
            dto.setBottleneckMessage(summary.totalMatching + " matching " + (summary.totalMatching == 1 ? "machine is" : "machines are") + " OFFLINE");
        } else if (summary.idleExec == 0) {
            dto.setBottleneckReason("ALL_EXECUTORS_BUSY");
            dto.setBottleneckMessage("All " + summary.busyExec + " executors on " + summary.onlineCount + " matching " + (summary.onlineCount == 1 ? "node are busy" : "nodes are busy"));
        } else if (dto.isBlocked()) {
            dto.setBottleneckReason("BLOCKED");
            dto.setBottleneckMessage(dto.getWhy() != null ? dto.getWhy() : "Build is blocked by dependencies");
        } else {
            dto.setBottleneckReason("READY");
            dto.setBottleneckMessage(summary.idleExec + " idle " + (summary.idleExec == 1 ? "executor available" : "executors available") + ", starting shortly");
        }
    }

    public static boolean cancelItem(long itemId) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return false;
        }

        Queue queue = jenkins.getQueue();
        Queue.Item item = queue.getItem(itemId);
        if (item != null) {
            if (!item.hasCancelPermission()) {
                jenkins.checkPermission(Jenkins.ADMINISTER);
            }
            try {
                boolean cancelled = queue.cancel(item);
                if (!cancelled) {
                    cancelled = queue.cancel(item.task);
                }
                return cancelled;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to cancel queue item " + itemId, e);
            }
        }
        return false;
    }
}
