package io.jenkins.plugins.fleetlens.monitoring;

import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import io.jenkins.plugins.fleetlens.api.dto.OsDto;
import io.jenkins.plugins.fleetlens.monitoring.callable.FetchOsInfoCallable;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Detects operating system family, human-readable name, distro, and CPU architecture.
 * Uses bounded background probing and positive/negative caching to prevent blocking HTTP threads.
 */
public class AgentOsDetector {

    private static final Logger LOGGER = Logger.getLogger(AgentOsDetector.class.getName());
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L; // 5 minutes

    private static final Map<String, CachedOs> OS_CACHE = new ConcurrentHashMap<>();

    private static final class CachedOs {
        final OsDto dto;
        final boolean onlineWhenSampled;
        final boolean distroProbed;
        final long timestamp;

        CachedOs(OsDto dto, boolean onlineWhenSampled, boolean distroProbed) {
            this.dto = dto;
            this.onlineWhenSampled = onlineWhenSampled;
            this.distroProbed = distroProbed;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_TTL_MS;
        }
    }

    /**
     * Asynchronously warms up the OS detection cache for an online computer if needed.
     */
    public static CompletableFuture<OsDto> warmupAsync(Computer computer) {
        if (computer == null || !computer.isOnline()) {
            return null;
        }
        String nodeName = getCacheKey(computer);
        CachedOs cached = OS_CACHE.get(nodeName);
        if (cached != null && !cached.isExpired() && cached.onlineWhenSampled && cached.distroProbed) {
            return null;
        }

        return FleetLensTelemetryExecutor.submitDeduplicated("os:" + nodeName, () -> probeOnlineOs(computer, nodeName, true));
    }

    public static OsDto detect(Computer computer) {
        if (computer == null) {
            return new OsDto("OTHER", "Unknown", "Unknown OS", "", "", "symbol-help");
        }

        String nodeName = getCacheKey(computer);
        boolean isOnline = computer.isOnline();

        CachedOs cached = OS_CACHE.get(nodeName);
        if (cached != null && !cached.isExpired() && cached.onlineWhenSampled == isOnline) {
            if (!isOnline || cached.distroProbed) {
                return cached.dto;
            }
        }

        // Offline path: fast in-memory label inspection or last known OS
        if (!isOnline) {
            if (cached != null && cached.onlineWhenSampled) {
                return cached.dto;
            }
            OsDto offlineDto = resolveOfflineOs(computer);
            OS_CACHE.put(nodeName, new CachedOs(offlineDto, false, true));
            return offlineDto;
        }

        // Online path: trigger background warmup if not already running or cached
        warmupAsync(computer);
        if (cached != null && cached.onlineWhenSampled) {
            return cached.dto;
        }

        // Fast non-blocking controller JVM path (zero RPC)
        if (computer.getName().isEmpty()) {
            return probeOnlineOs(computer, nodeName, false);
        }

        // Fast in-memory node label fallback while background probe completes asynchronously
        OsDto labelDto = resolveOfflineOs(computer);
        if (!"OTHER".equals(labelDto.getFamily())) {
            return labelDto;
        }

        // Return safe fallback without blocking HTTP thread on remoting RPC
        return new OsDto("OTHER", "Unknown", "Unknown OS", "", "", "symbol-help");
    }

    private static OsDto resolveOfflineOs(Computer computer) {
        Node node = computer.getNode();
        if (node != null) {
            for (Label l : node.getAssignedLabels()) {
                String lname = l.getName().toLowerCase(Locale.ROOT);
                if (lname.contains("mac") || lname.contains("darwin") || lname.contains("apple")) {
                    return new OsDto("MACOS", "macOS", "macOS (Apple)", "", "", "symbol-apple");
                }
                if (lname.contains("win") || lname.contains("windows")) {
                    return new OsDto("WINDOWS", "Windows", "Windows", "", "", "symbol-windows");
                }
                if (lname.contains("linux") || lname.contains("ubuntu") || lname.contains("debian") || lname.contains("alpine")) {
                    return new OsDto("LINUX", "Linux", "Linux", "", "", "symbol-linux");
                }
            }
        }
        return new OsDto("OTHER", "Offline", "Offline Agent", "", "", "symbol-help");
    }

    private static OsDto probeOnlineOs(Computer computer, String nodeName, boolean allowRemoteCallable) {
        Map<Object, Object> sysProps = null;
        try {
            sysProps = computer.getSystemProperties();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to read system properties from " + nodeName, e);
        }

        String rawName = sysProps != null && sysProps.containsKey("os.name") ? String.valueOf(sysProps.get("os.name")) : "Unknown";
        String rawVersion = sysProps != null && sysProps.containsKey("os.version") ? String.valueOf(sysProps.get("os.version")) : "";
        String rawArch = sysProps != null && sysProps.containsKey("os.arch") ? String.valueOf(sysProps.get("os.arch")) : "";

        String family = resolveFamily(rawName);
        String prettyName = rawName;
        String icon = "symbol-terminal";
        boolean distroProbed = true;

        switch (family) {
            case "LINUX":
                icon = "symbol-linux";
                if (allowRemoteCallable) {
                    prettyName = resolveLinuxDistro(computer, nodeName, rawName, rawVersion);
                } else {
                    prettyName = !rawVersion.isEmpty() ? "Linux (" + rawVersion + ")" : rawName;
                    distroProbed = false;
                }
                break;
            case "WINDOWS":
                icon = "symbol-windows";
                prettyName = rawName;
                break;
            case "MACOS":
                icon = "symbol-apple";
                prettyName = resolveMacName(rawVersion);
                break;
            case "BSD":
                icon = "symbol-server";
                prettyName = rawName + " " + rawVersion;
                break;
            default:
                icon = "symbol-hardware-chip";
                prettyName = rawName;
                break;
        }

        String normalizedArch = normalizeArch(rawArch);
        OsDto dto = new OsDto(family, rawName, prettyName, rawVersion, normalizedArch, icon);

        // Only overwrite cache if distro was probed or no probed entry exists yet
        CachedOs existing = OS_CACHE.get(nodeName);
        if (distroProbed || existing == null || !existing.distroProbed) {
            OS_CACHE.put(nodeName, new CachedOs(dto, true, distroProbed));
        }
        return dto;
    }

    private static String getCacheKey(Computer computer) {
        String nodeName = computer.getName();
        return nodeName.isEmpty() ? "(controller)" : nodeName;
    }

    private static String resolveFamily(String osName) {
        if (osName == null) return "OTHER";
        String lower = osName.toLowerCase(Locale.ROOT);
        if (lower.contains("linux")) return "LINUX";
        if (lower.contains("windows")) return "WINDOWS";
        if (lower.contains("mac") || lower.contains("darwin")) return "MACOS";
        if (lower.contains("bsd")) return "BSD";
        if (lower.contains("solaris") || lower.contains("sunos")) return "SOLARIS";
        return "OTHER";
    }

    private static String resolveMacName(String osVersion) {
        if (osVersion == null || osVersion.isEmpty()) return "macOS";
        if (osVersion.startsWith("15.") || osVersion.startsWith("24.")) return "macOS 15 (Sequoia)";
        if (osVersion.startsWith("14.") || osVersion.startsWith("23.")) return "macOS 14 (Sonoma)";
        if (osVersion.startsWith("13.") || osVersion.startsWith("22.")) return "macOS 13 (Ventura)";
        if (osVersion.startsWith("12.") || osVersion.startsWith("21.")) return "macOS 12 (Monterey)";
        return "macOS " + osVersion;
    }

    private static String resolveLinuxDistro(Computer computer, String nodeName, String defaultName, String version) {
        CachedOs cached = OS_CACHE.get(nodeName);
        if (cached != null && cached.distroProbed && cached.onlineWhenSampled) {
            return cached.dto.getPrettyName();
        }

        VirtualChannel channel = computer.getChannel();
        if (channel != null) {
            try {
                String probedName = channel.call(new FetchOsInfoCallable());
                if (probedName != null && !probedName.trim().isEmpty()) {
                    return probedName.trim();
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.FINE, "Failed to probe Linux os-release for " + nodeName, e);
            }
        }
        return version != null && !version.isEmpty() ? "Linux (" + version + ")" : defaultName;
    }

    private static String normalizeArch(String arch) {
        if (arch == null || arch.isEmpty()) return "";
        String lower = arch.toLowerCase(Locale.ROOT);
        if (lower.equals("x86_64") || lower.equals("amd64")) return "amd64";
        if (lower.equals("aarch64") || lower.equals("arm64")) return "arm64";
        if (lower.contains("x86") || lower.equals("i386") || lower.equals("i686")) return "x86";
        return arch;
    }

    public static void clearCache(String nodeName) {
        if (nodeName != null) {
            OS_CACHE.remove(nodeName);
        }
    }
}
