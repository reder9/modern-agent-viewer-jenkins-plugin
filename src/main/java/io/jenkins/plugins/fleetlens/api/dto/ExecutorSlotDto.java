package io.jenkins.plugins.fleetlens.api.dto;

import java.io.Serializable;

/**
 * Data Transfer Object representing an individual executor slot on a node.
 */
public class ExecutorSlotDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private int number;
    private boolean busy;
    private String currentExecutable;
    private String currentExecutableUrl;
    private int progress = -1;
    private String durationHuman;

    public ExecutorSlotDto() {}

    public ExecutorSlotDto(int number, boolean busy, String currentExecutable, String currentExecutableUrl, int progress, String durationHuman) {
        this.number = number;
        this.busy = busy;
        this.currentExecutable = currentExecutable;
        this.currentExecutableUrl = currentExecutableUrl;
        this.progress = progress;
        this.durationHuman = durationHuman;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public boolean isBusy() {
        return busy;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    public String getCurrentExecutable() {
        return currentExecutable;
    }

    public void setCurrentExecutable(String currentExecutable) {
        this.currentExecutable = currentExecutable;
    }

    public String getCurrentExecutableUrl() {
        return currentExecutableUrl;
    }

    public void setCurrentExecutableUrl(String currentExecutableUrl) {
        this.currentExecutableUrl = currentExecutableUrl;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public String getDurationHuman() {
        return durationHuman;
    }

    public void setDurationHuman(String durationHuman) {
        this.durationHuman = durationHuman;
    }
}
