package io.jenkins.plugins.fleetlens.api.dto;

import java.io.Serializable;

/**
 * Data Transfer Object representing disk storage and temp metrics.
 */
public class StorageDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean available;
    private String rootPath;
    private long totalBytes = -1;
    private long usedBytes = -1;
    private long freeBytes = -1;
    private int percentUsed = -1;
    private String healthStatus = "UNKNOWN"; // HEALTHY, WARNING, CRITICAL, UNKNOWN
    private String totalHuman = "--";
    private String usedHuman = "--";
    private String freeHuman = "--";

    private boolean tempAvailable;
    private long tempFreeBytes = -1;
    private String tempFreeHuman = "--";

    public StorageDto() {}

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public void setUsedBytes(long usedBytes) {
        this.usedBytes = usedBytes;
    }

    public long getFreeBytes() {
        return freeBytes;
    }

    public void setFreeBytes(long freeBytes) {
        this.freeBytes = freeBytes;
    }

    public int getPercentUsed() {
        return percentUsed;
    }

    public void setPercentUsed(int percentUsed) {
        this.percentUsed = percentUsed;
    }

    public String getHealthStatus() {
        return healthStatus;
    }

    public void setHealthStatus(String healthStatus) {
        this.healthStatus = healthStatus;
    }

    public String getTotalHuman() {
        return totalHuman;
    }

    public void setTotalHuman(String totalHuman) {
        this.totalHuman = totalHuman;
    }

    public String getUsedHuman() {
        return usedHuman;
    }

    public void setUsedHuman(String usedHuman) {
        this.usedHuman = usedHuman;
    }

    public String getFreeHuman() {
        return freeHuman;
    }

    public void setFreeHuman(String freeHuman) {
        this.freeHuman = freeHuman;
    }

    public boolean isTempAvailable() {
        return tempAvailable;
    }

    public void setTempAvailable(boolean tempAvailable) {
        this.tempAvailable = tempAvailable;
    }

    public long getTempFreeBytes() {
        return tempFreeBytes;
    }

    public void setTempFreeBytes(long tempFreeBytes) {
        this.tempFreeBytes = tempFreeBytes;
    }

    public String getTempFreeHuman() {
        return tempFreeHuman;
    }

    public void setTempFreeHuman(String tempFreeHuman) {
        this.tempFreeHuman = tempFreeHuman;
    }
}
