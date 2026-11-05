package io.jenkins.plugins.fleetlens.monitoring;

import hudson.model.Computer;
import hudson.model.Node;
import hudson.node_monitors.DiskSpaceMonitorDescriptor;
import hudson.remoting.VirtualChannel;
import io.jenkins.plugins.fleetlens.api.dto.StorageDto;
import io.jenkins.plugins.fleetlens.monitoring.callable.FetchStorageCallable;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for querying and formatting storage and disk space metrics for agents.
 * Uses non-blocking background probes and Jenkins NodeMonitor instant fallbacks.
 */
public class AgentStorageService {

    private static final Logger LOGGER = Logger.getLogger(AgentStorageService.class.getName());
    private static final long CACHE_TTL_MS = 90_000L; // 90 seconds cache

    private static final Map<String, CacheEntry> STORAGE_CACHE = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final StorageDto dto;
        final boolean onlineWhenSampled;
        final boolean fullProbeCompleted;
        final long timestamp;

        CacheEntry(StorageDto dto, boolean onlineWhenSampled, boolean fullProbeCompleted) {
            this.dto = dto;
            this.onlineWhenSampled = onlineWhenSampled;
            this.fullProbeCompleted = fullProbeCompleted;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_TTL_MS;
        }
    }

    /**
     * Asynchronously warms up the storage cache for an online computer via background RPC.
     */
    public static CompletableFuture<StorageDto> warmupAsync(Computer computer, Node node) {
        if (computer == null || !computer.isOnline()) {
            return null;
        }
        String nodeName = getCacheKey(computer);
        CacheEntry cached = STORAGE_CACHE.get(nodeName);
        if (cached != null && !cached.isExpired() && cached.onlineWhenSampled && cached.fullProbeCompleted) {
            return null;
        }

        return FleetLensTelemetryExecutor.submitDeduplicated("storage:" + nodeName, () -> probeOnlineStorage(computer, node, nodeName));
    }

    public static StorageDto getStorageMetrics(Computer computer, Node node) {
        if (computer == null) {
            return new StorageDto();
        }

        String nodeName = getCacheKey(computer);
        boolean isOnline = computer.isOnline();

        CacheEntry cached = STORAGE_CACHE.get(nodeName);
        if (cached != null && !cached.isExpired() && cached.onlineWhenSampled == isOnline) {
            if (!isOnline || cached.fullProbeCompleted) {
                return cached.dto;
            }
        }

        if (!isOnline) {
            if (cached != null && cached.onlineWhenSampled) {
                return cached.dto;
            }
            StorageDto dto = new StorageDto();
            dto.setRootPath(resolveRootPath(computer, node));
            dto.setAvailable(false);
            dto.setHealthStatus("UNKNOWN");
            STORAGE_CACHE.put(nodeName, new CacheEntry(dto, false, true));
            return dto;
        }

        // Online cache miss or expired: trigger background probe
        warmupAsync(computer, node);

        // Re-check if warmup completed or previous online sample exists
        cached = STORAGE_CACHE.get(nodeName);
        if (cached != null && cached.onlineWhenSampled) {
            return cached.dto;
        }

        // Instant non-blocking fallback from Jenkins in-memory NodeMonitors while background probe is in-flight
        StorageDto fallbackDto = new StorageDto();
        fallbackDto.setRootPath(resolveRootPath(computer, node));
        applyNodeMonitorFallbacks(computer, fallbackDto);
        return fallbackDto;
    }

    private static StorageDto probeOnlineStorage(Computer computer, Node node, String nodeName) {
        StorageDto dto = new StorageDto();
        String rootPath = resolveRootPath(computer, node);
        dto.setRootPath(rootPath);

        VirtualChannel channel = computer.getChannel();
        boolean probedSuccessfully = false;

        if (channel != null) {
            try {
                FetchStorageCallable.StorageResult res = channel.call(new FetchStorageCallable(rootPath));
                if (res != null && res.rootAvailable) {
                    dto.setAvailable(true);
                    dto.setTotalBytes(res.rootTotalBytes);
                    dto.setUsedBytes(res.rootUsedBytes);
                    dto.setFreeBytes(res.rootFreeBytes);
                    dto.setTotalHuman(formatBytes(res.rootTotalBytes));
                    dto.setUsedHuman(formatBytes(res.rootUsedBytes));
                    dto.setFreeHuman(formatBytes(res.rootFreeBytes));

                    if (res.rootTotalBytes > 0) {
                        int pct = (int) Math.round(((double) res.rootUsedBytes / res.rootTotalBytes) * 100.0);
                        dto.setPercentUsed(Math.max(0, Math.min(100, pct)));
                        dto.setHealthStatus(calculateHealth(pct, res.rootFreeBytes));
                    }

                    if (res.tempAvailable) {
                        dto.setTempAvailable(true);
                        dto.setTempFreeBytes(res.tempFreeBytes);
                        dto.setTempFreeHuman(formatBytes(res.tempFreeBytes));
                    }
                    probedSuccessfully = true;
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Failed to execute storage probe on " + nodeName, e);
            }
        }

        if (!probedSuccessfully) {
            applyNodeMonitorFallbacks(computer, dto);
        }

        STORAGE_CACHE.put(nodeName, new CacheEntry(dto, true, true));
        return dto;
    }

    private static String getCacheKey(Computer computer) {
        String nodeName = computer.getName();
        return nodeName.isEmpty() ? "(controller)" : nodeName;
    }

    private static String resolveRootPath(Computer computer, Node node) {
        if (node != null) {
            hudson.FilePath rootPath = node.getRootPath();
            if (rootPath != null) {
                return rootPath.getRemote();
            }
        }
        if (computer.getName().isEmpty()) {
            jenkins.model.Jenkins j = jenkins.model.Jenkins.getInstanceOrNull();
            if (j != null) {
                return j.getRootDir().getAbsolutePath();
            }
        }
        return ".";
    }

    private static void applyNodeMonitorFallbacks(Computer computer, StorageDto dto) {
        try {
            Map<String, Object> monitorData = computer.getMonitorData();
            if (monitorData != null) {
                Object diskObj = monitorData.get("hudson.node_monitors.DiskSpaceMonitor");
                if (diskObj instanceof DiskSpaceMonitorDescriptor.DiskSpace) {
                    DiskSpaceMonitorDescriptor.DiskSpace disk = (DiskSpaceMonitorDescriptor.DiskSpace) diskObj;
                    long free = disk.size;
                    dto.setAvailable(true);
                    dto.setFreeBytes(free);
                    dto.setFreeHuman(formatBytes(free));
                    dto.setHealthStatus(calculateHealth(50, free));
                }

                Object tempObj = monitorData.get("hudson.node_monitors.TemporarySpaceMonitor");
                if (tempObj instanceof DiskSpaceMonitorDescriptor.DiskSpace) {
                    DiskSpaceMonitorDescriptor.DiskSpace tempDisk = (DiskSpaceMonitorDescriptor.DiskSpace) tempObj;
                    long free = tempDisk.size;
                    dto.setTempAvailable(true);
                    dto.setTempFreeBytes(free);
                    dto.setTempFreeHuman(formatBytes(free));
                }
            }
        } catch (RuntimeException ignored) {
            LOGGER.log(Level.FINE, "Failed to apply node monitor fallback for " + computer.getName(), ignored);
        }
    }

    private static String calculateHealth(int percentUsed, long freeBytes) {
        if (percentUsed >= 90 || (freeBytes > 0 && freeBytes < 2L * 1024 * 1024 * 1024)) {
            return "CRITICAL";
        }
        if (percentUsed >= 78 || (freeBytes > 0 && freeBytes < 5L * 1024 * 1024 * 1024)) {
            return "WARNING";
        }
        return "HEALTHY";
    }

    public static String formatBytes(long bytes) {
        if (bytes < 0) return "--";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024, exp), pre);
    }

    public static void clearCache(String nodeName) {
        if (nodeName != null) {
            STORAGE_CACHE.remove(nodeName);
        }
    }
}
