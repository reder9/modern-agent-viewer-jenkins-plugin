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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Detects operating system family, human-readable name, distro, and CPU architecture.
 */
public class AgentOsDetector {

    private static final Logger LOGGER = Logger.getLogger(AgentOsDetector.class.getName());
    private static final Map<String, OsDto> OS_CACHE = new ConcurrentHashMap<>();

    public static OsDto detect(Computer computer) {
        if (computer == null) {
            return new OsDto("OTHER", "Unknown", "Unknown OS", "", "", "symbol-help");
        }

        String nodeName = computer.getName();
        if (nodeName.isEmpty()) {
            nodeName = "(controller)";
        }

        // Check if agent is online; if offline, return cached or fallback
        if (!computer.isOnline()) {
            OsDto cached = OS_CACHE.get(nodeName);
            if (cached != null) {
                return cached;
            }
            // Check node labels for offline OS hint
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

        switch (family) {
            case "LINUX":
                icon = "symbol-linux";
                prettyName = resolveLinuxDistro(computer, nodeName, rawName, rawVersion);
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

        // Normalize arch
        String normalizedArch = normalizeArch(rawArch);

        OsDto dto = new OsDto(family, rawName, prettyName, rawVersion, normalizedArch, icon);
        OS_CACHE.put(nodeName, dto);
        return dto;
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
        // Version mapping: Darwin / macOS
        if (osVersion.startsWith("15.") || osVersion.startsWith("24.")) return "macOS 15 (Sequoia)";
        if (osVersion.startsWith("14.") || osVersion.startsWith("23.")) return "macOS 14 (Sonoma)";
        if (osVersion.startsWith("13.") || osVersion.startsWith("22.")) return "macOS 13 (Ventura)";
        if (osVersion.startsWith("12.") || osVersion.startsWith("21.")) return "macOS 12 (Monterey)";
        return "macOS " + osVersion;
    }

    private static String resolveLinuxDistro(Computer computer, String nodeName, String defaultName, String version) {
        OsDto cached = OS_CACHE.get(nodeName);
        if (cached != null && !cached.getPrettyName().equalsIgnoreCase("Linux")) {
            return cached.getPrettyName();
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
