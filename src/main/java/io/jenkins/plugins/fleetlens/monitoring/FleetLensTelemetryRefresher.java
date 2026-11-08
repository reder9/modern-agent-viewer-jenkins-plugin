package io.jenkins.plugins.fleetlens.monitoring;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight periodic background worker that pre-warms fleet telemetry caches,
 * ensuring fast sub-20ms page loads even after idle periods on large instances.
 */
@Extension
public class FleetLensTelemetryRefresher extends AsyncPeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(FleetLensTelemetryRefresher.class.getName());

    public FleetLensTelemetryRefresher() {
        super("Modern Agent Viewer Telemetry Refresher");
    }

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.MINUTES.toMillis(2); // Runs every 2 minutes in background
    }

    @Override
    protected void execute(TaskListener listener) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return;
        }
        try {
            FleetLensTelemetryExecutor.warmupComputers(jenkins.getComputers(), 1000L);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Periodic fleet telemetry pre-warming encountered an issue", e);
        }
    }
}
