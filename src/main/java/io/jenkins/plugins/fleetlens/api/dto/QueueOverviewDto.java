package io.jenkins.plugins.fleetlens.api.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Summary DTO of the Jenkins build queue and pending agent demand.
 */
public class QueueOverviewDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private int totalWaiting;
    private int stuckCount;
    private int blockedCount;
    private int buildableCount;
    private List<QueueItemDto> items = new ArrayList<>();

    public QueueOverviewDto() {}

    public int getTotalWaiting() {
        return totalWaiting;
    }

    public void setTotalWaiting(int totalWaiting) {
        this.totalWaiting = totalWaiting;
    }

    public int getStuckCount() {
        return stuckCount;
    }

    public void setStuckCount(int stuckCount) {
        this.stuckCount = stuckCount;
    }

    public int getBlockedCount() {
        return blockedCount;
    }

    public void setBlockedCount(int blockedCount) {
        this.blockedCount = blockedCount;
    }

    public int getBuildableCount() {
        return buildableCount;
    }

    public void setBuildableCount(int buildableCount) {
        this.buildableCount = buildableCount;
    }

    public List<QueueItemDto> getItems() {
        return items;
    }

    public void setItems(List<QueueItemDto> items) {
        this.items = items != null ? items : new ArrayList<>();
    }
}
