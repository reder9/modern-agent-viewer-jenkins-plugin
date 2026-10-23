package io.jenkins.plugins.fleetlens.monitoring.callable;

import java.io.Serializable;

/**
 * Encapsulates the execution result of an ad-hoc diagnostic command on a build node.
 */
public class DiagnosticResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String command;
    private final int exitCode;
    private final String output;
    private final long durationMs;
    private final boolean timedOut;

    public DiagnosticResult(String command, int exitCode, String output, long durationMs, boolean timedOut) {
        this.command = command != null ? command : "";
        this.exitCode = exitCode;
        this.output = output != null ? output : "";
        this.durationMs = durationMs;
        this.timedOut = timedOut;
    }

    public String getCommand() {
        return command;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getOutput() {
        return output;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isTimedOut() {
        return timedOut;
    }
}
