package io.jenkins.plugins.fleetlens.monitoring;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import hudson.slaves.OfflineCause;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Event-driven telemetry pre-warming and cache eviction listener.
 * Warms up node telemetry in the background immediately when an agent connects,
 * and clears caches immediately when an agent disconnects.
 */
@Extension
public class FleetLensComputerListener extends ComputerListener {

    private static final Logger LOGGER = Logger.getLogger(FleetLensComputerListener.class.getName());

    @Override
    public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
        if (c != null) {
            try {
                FleetLensTelemetryExecutor.warmupSingleComputer(c, c.getNode());
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Failed to pre-warm telemetry for connecting agent " + c.getName(), e);
            }
        }
    }

    @Override
    public void onOffline(Computer c, OfflineCause cause) {
        if (c != null) {
            String name = c.getName();
            AgentOsDetector.clearCache(name);
            AgentStorageService.clearCache(name);
            AgentUptimeService.clearCache(name);
            AgentNetworkService.clearCache(name);
            AgentConnectionService.clearCache(name);
            AgentJvmDetector.clearCache(name);
        }
    }
}
