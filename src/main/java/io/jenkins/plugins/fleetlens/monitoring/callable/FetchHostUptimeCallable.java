package io.jenkins.plugins.fleetlens.monitoring.callable;

import jenkins.security.MasterToSlaveCallable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;

/**
 * Callable executed on the agent to query the host OS boot uptime.
 */
public class FetchHostUptimeCallable extends MasterToSlaveCallable<Long, RuntimeException> {

    private static final long serialVersionUID = 1L;

    @Override
    public Long call() {
        // 1. Linux: Check /proc/uptime
        File procUptime = new File("/proc/uptime");
        if (procUptime.exists() && procUptime.canRead()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(procUptime), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length > 0) {
                        double seconds = Double.parseDouble(parts[0]);
                        return (long) (seconds * 1000.0);
                    }
                }
            } catch (IOException | NumberFormatException ignored) {
                // Fallback to JVM uptime
            }
        }

        // 2. Fallback for all OSs: JVM RuntimeMXBean uptime (guaranteed minimum uptime)
        try {
            return ManagementFactory.getRuntimeMXBean().getUptime();
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }
}
