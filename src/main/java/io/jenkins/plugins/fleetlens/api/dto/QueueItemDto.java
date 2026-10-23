package io.jenkins.plugins.fleetlens.api.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Data transfer object representing a waiting or stuck build queue item.
 */
public class QueueItemDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private long id;
    private String name;
    private String url;
    private String why;
    private String assignedLabel;
    private long inQueueSince;
    private long inQueueDurationMs;
    private String inQueueDurationHuman;
    private boolean blocked;
    private boolean buildable;
    private boolean stuck;
    private boolean canCancel;

    // Bottleneck analysis
    private int matchingNodesCount;
    private int onlineMatchingNodesCount;
    private int idleExecutorsCount;
    private int busyExecutorsCount;
    private List<String> matchingNodeNames = new ArrayList<>();
    private String bottleneckReason;
    private String bottleneckMessage;

    public QueueItemDto() {}

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getWhy() {
        return why;
    }

    public void setWhy(String why) {
        this.why = why;
    }

    public String getAssignedLabel() {
        return assignedLabel;
    }

    public void setAssignedLabel(String assignedLabel) {
        this.assignedLabel = assignedLabel;
    }

    public long getInQueueSince() {
        return inQueueSince;
    }

    public void setInQueueSince(long inQueueSince) {
        this.inQueueSince = inQueueSince;
    }

    public long getInQueueDurationMs() {
        return inQueueDurationMs;
    }

    public void setInQueueDurationMs(long inQueueDurationMs) {
        this.inQueueDurationMs = inQueueDurationMs;
    }

    public String getInQueueDurationHuman() {
        return inQueueDurationHuman;
    }

    public void setInQueueDurationHuman(String inQueueDurationHuman) {
        this.inQueueDurationHuman = inQueueDurationHuman;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public boolean isBuildable() {
        return buildable;
    }

    public void setBuildable(boolean buildable) {
        this.buildable = buildable;
    }

    public boolean isStuck() {
        return stuck;
    }

    public void setStuck(boolean stuck) {
        this.stuck = stuck;
    }

    public boolean isCanCancel() {
        return canCancel;
    }

    public void setCanCancel(boolean canCancel) {
        this.canCancel = canCancel;
    }

    public int getMatchingNodesCount() {
        return matchingNodesCount;
    }

    public void setMatchingNodesCount(int matchingNodesCount) {
        this.matchingNodesCount = matchingNodesCount;
    }

    public int getOnlineMatchingNodesCount() {
        return onlineMatchingNodesCount;
    }

    public void setOnlineMatchingNodesCount(int onlineMatchingNodesCount) {
        this.onlineMatchingNodesCount = onlineMatchingNodesCount;
    }

    public int getIdleExecutorsCount() {
        return idleExecutorsCount;
    }

    public void setIdleExecutorsCount(int idleExecutorsCount) {
        this.idleExecutorsCount = idleExecutorsCount;
    }

    public int getBusyExecutorsCount() {
        return busyExecutorsCount;
    }

    public void setBusyExecutorsCount(int busyExecutorsCount) {
        this.busyExecutorsCount = busyExecutorsCount;
    }

    public List<String> getMatchingNodeNames() {
        return matchingNodeNames;
    }

    public void setMatchingNodeNames(List<String> matchingNodeNames) {
        this.matchingNodeNames = matchingNodeNames != null ? matchingNodeNames : new ArrayList<>();
    }

    public String getBottleneckReason() {
        return bottleneckReason;
    }

    public void setBottleneckReason(String bottleneckReason) {
        this.bottleneckReason = bottleneckReason;
    }

    public String getBottleneckMessage() {
        return bottleneckMessage;
    }

    public void setBottleneckMessage(String bottleneckMessage) {
        this.bottleneckMessage = bottleneckMessage;
    }
}
