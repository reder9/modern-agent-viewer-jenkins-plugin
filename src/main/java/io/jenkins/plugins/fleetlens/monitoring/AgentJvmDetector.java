package io.jenkins.plugins.fleetlens.monitoring;

import hudson.model.Computer;
import io.jenkins.plugins.fleetlens.api.dto.JvmDto;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Detects agent JVM version, resolves controller JVM version, and flags version mismatches.
 */
public final class AgentJvmDetector {

    private static final Logger LOGGER = Logger.getLogger(AgentJvmDetector.class.getName());
    private static final long JVM_CACHE_TTL_MS = 600_000L; // 10 minutes (JVM version does not change per connection)
    private static final Map<String, CachedJvm> JVM_CACHE = new ConcurrentHashMap<>();

    private static final int CONTROLLER_MAJOR_VERSION = resolveControllerMajorVersion();

    private AgentJvmDetector() {}

    private static class CachedJvm {
        final JvmDto dto;
        final long timestamp;

        CachedJvm(JvmDto dto) {
            this.dto = dto;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > JVM_CACHE_TTL_MS;
        }
    }

    public static int getControllerMajorVersion() {
        return CONTROLLER_MAJOR_VERSION;
    }

    public static int parseMajorVersion(String version) {
        if (version == null || version.trim().isEmpty()) {
            return -1;
        }
        try {
            String v = version.trim();
            if (v.startsWith("1.")) {
                String[] parts = v.split("\\.");
                if (parts.length > 1) {
                    return Integer.parseInt(parts[1]);
                }
            }
            int dot = v.indexOf('.');
            int dash = v.indexOf('-');
            int plus = v.indexOf('+');
            int end = v.length();
            if (dot > 0) end = Math.min(end, dot);
            if (dash > 0) end = Math.min(end, dash);
            if (plus > 0) end = Math.min(end, plus);
            return Integer.parseInt(v.substring(0, end));
        } catch (Exception e) {
            return -1;
        }
    }

    private static int resolveControllerMajorVersion() {
        try {
            return Runtime.version().feature();
        } catch (Throwable t) {
            return parseMajorVersion(System.getProperty("java.version"));
        }
    }

    public static void clearCache(String nodeName) {
        if (nodeName != null) {
            JVM_CACHE.remove(nodeName);
            if (nodeName.isEmpty() || "(controller)".equals(nodeName)) {
                JVM_CACHE.remove("");
                JVM_CACHE.remove("(controller)");
            }
        }
    }

    public static CompletableFuture<JvmDto> warmupAsync(Computer computer) {
        if (computer == null || !computer.isOnline()) {
            return null;
        }
        String nodeName = computer.getName();
        String cacheKey = nodeName.isEmpty() ? "(controller)" : nodeName;
        CachedJvm cached = JVM_CACHE.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return null;
        }

        return FleetLensTelemetryExecutor.submitDeduplicated("jvm:" + cacheKey, () -> probeJvm(computer, cacheKey));
    }

    public static JvmDto detect(Computer computer) {
        if (computer == null) {
            return null;
        }
        String nodeName = computer.getName();
        String cacheKey = nodeName.isEmpty() ? "(controller)" : nodeName;

        CachedJvm cached = JVM_CACHE.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.dto;
        }

        // Fast controller path (0ms, zero remoting RPC)
        if (nodeName.isEmpty()) {
            return probeJvm(computer, cacheKey);
        }

        if (!computer.isOnline()) {
            return cached != null ? cached.dto : null;
        }

        warmupAsync(computer);
        return cached != null ? cached.dto : null;
    }

    private static JvmDto probeJvm(Computer computer, String cacheKey) {
        String rawVersion = null;
        if (computer.getName().isEmpty()) {
            rawVersion = System.getProperty("java.version");
        } else {
            try {
                Map<Object, Object> props = computer.getSystemProperties();
                if (props != null) {
                    if (props.containsKey("java.version")) {
                        rawVersion = String.valueOf(props.get("java.version"));
                    } else if (props.containsKey("java.runtime.version")) {
                        rawVersion = String.valueOf(props.get("java.runtime.version"));
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Failed to inspect JVM properties for " + cacheKey, e);
            }
        }

        if (rawVersion == null || rawVersion.trim().isEmpty()) {
            return null;
        }

        int agentMajor = parseMajorVersion(rawVersion);
        int ctrlMajor = CONTROLLER_MAJOR_VERSION;
        boolean mismatch = agentMajor > 0 && ctrlMajor > 0 && agentMajor != ctrlMajor;
        String warning = null;
        if (mismatch) {
            warning = "Agent JVM (Java " + agentMajor + ") does not match controller JVM (Java " + ctrlMajor + "). Remoting serialization errors may occur.";
        }

        JvmDto dto = new JvmDto(rawVersion, agentMajor, ctrlMajor, mismatch, warning);
        JVM_CACHE.put(cacheKey, new CachedJvm(dto));
        return dto;
    }
}
