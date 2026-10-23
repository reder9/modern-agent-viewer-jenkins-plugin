package io.jenkins.plugins.fleetlens.monitoring;

import hudson.model.Computer;
import hudson.remoting.VirtualChannel;
import io.jenkins.plugins.fleetlens.api.dto.TimeMetricsDto;
import io.jenkins.plugins.fleetlens.monitoring.callable.FetchHostUptimeCallable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for tracking connection duration and querying host OS boot uptime.
 */
public class AgentUptimeService {

    private static final Logger LOGGER = Logger.getLogger(AgentUptimeService.class.getName());
    private static final long UPTIME_CACHE_TTL_MS = 120_000L; // 2 minutes

    private static final Map<String, UptimeCacheEntry> HOST_UPTIME_CACHE = new ConcurrentHashMap<>();

    private static class UptimeCacheEntry {
        final long baseUptimeMs;
        final long sampledAtMs;

        UptimeCacheEntry(long baseUptimeMs) {
            this.baseUptimeMs = baseUptimeMs;
            this.sampledAtMs = System.currentTimeMillis();
        }

        long getCurrentEstimatedUptime() {
            if (baseUptimeMs < 0) return -1;
            return baseUptimeMs + (System.currentTimeMillis() - sampledAtMs);
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - sampledAtMs) > UPTIME_CACHE_TTL_MS;
        }
    }

    public static TimeMetricsDto getTimeMetrics(Computer computer) {
        TimeMetricsDto dto = new TimeMetricsDto();
        if (computer == null) {
            return dto;
        }

        String nodeName = computer.getName();
        if (nodeName.isEmpty()) {
            nodeName = "(controller)";
        }

        boolean isOnline = computer.isOnline();
        dto.setConnected(isOnline);

        if (isOnline) {
            long connectTime = computer.getConnectTime();
            if (connectTime > 0) {
                dto.setConnectTimestamp(connectTime);
                long duration = Math.max(0, System.currentTimeMillis() - connectTime);
                dto.setConnectedDurationMs(duration);
                dto.setConnectedDurationHuman(formatDuration(duration));
            } else {
                dto.setConnectedDurationHuman("Connected");
            }

            // Probe host uptime
            long hostUptime = getHostUptime(computer, nodeName);
            if (hostUptime > 0) {
                dto.setHostUptimeMs(hostUptime);
                dto.setHostUptimeHuman(formatDuration(hostUptime));
                dto.setBootTimestamp(System.currentTimeMillis() - hostUptime);
            }
        } else {
            dto.setConnectedDurationHuman("Disconnected");
            dto.setHostUptimeHuman("Offline");
        }

        return dto;
    }

    private static long getHostUptime(Computer computer, String nodeName) {
        UptimeCacheEntry entry = HOST_UPTIME_CACHE.get(nodeName);
        if (entry != null && !entry.isExpired()) {
            return entry.getCurrentEstimatedUptime();
        }

        VirtualChannel channel = computer.getChannel();
        if (channel != null) {
            try {
                Long uptime = channel.call(new FetchHostUptimeCallable());
                if (uptime != null && uptime > 0) {
                    HOST_UPTIME_CACHE.put(nodeName, new UptimeCacheEntry(uptime));
                    return uptime;
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Failed to fetch host uptime for " + nodeName, e);
            }
        }

        if (entry != null) {
            return entry.getCurrentEstimatedUptime();
        }
        return -1L;
    }

    public static String formatDuration(long millis) {
        if (millis < 0) return "--";
        long seconds = millis / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m " + (seconds % 60) + "s";
        }
        long hours = minutes / 60;
        long remMinutes = minutes % 60;
        if (hours < 24) {
            return hours + "h " + remMinutes + "m";
        }
        long days = hours / 24;
        long remHours = hours % 24;
        return days + "d " + remHours + "h";
    }

    public static void clearCache(String nodeName) {
        if (nodeName != null) {
            HOST_UPTIME_CACHE.remove(nodeName);
        }
    }
}
