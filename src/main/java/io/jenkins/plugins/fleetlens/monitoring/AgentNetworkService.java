package io.jenkins.plugins.fleetlens.monitoring;

import hudson.EnvVars;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.remoting.VirtualChannel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;
import io.jenkins.plugins.fleetlens.monitoring.callable.FetchAgentNetworkCallable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Service for discovering and verifying agent network IP addresses and hostnames.
 * Uses non-blocking background probes and positive/negative caching so slow DNS or
 * unresponsive channels never block HTTP request threads.
 */
public final class AgentNetworkService {

    private static final Logger LOGGER = Logger.getLogger(AgentNetworkService.class.getName());
    private static final Map<String, CachedNetwork> NETWORK_CACHE = new ConcurrentHashMap<>();
    private static final long ONLINE_CACHE_TTL_MS = 5 * 60 * 1000L;  // 5 minutes
    private static final long OFFLINE_CACHE_TTL_MS = 2 * 60 * 1000L; // 2 minutes

    private static final Pattern IPV4_PATTERN =
            Pattern.compile("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.){3}(25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)$");

    private AgentNetworkService() {}

    private static final class CachedNetwork {
        final String hostName;
        final String ip;
        final boolean onlineWhenSampled;
        final long timestamp;

        CachedNetwork(String hostName, String ip, boolean onlineWhenSampled) {
            this.hostName = hostName;
            this.ip = ip;
            this.onlineWhenSampled = onlineWhenSampled;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            long ttl = onlineWhenSampled ? ONLINE_CACHE_TTL_MS : OFFLINE_CACHE_TTL_MS;
            return (System.currentTimeMillis() - timestamp) > ttl;
        }
    }

    /**
     * Asynchronously warms up the network hostname and IP cache for a computer.
     */
    public static CompletableFuture< Void > warmupAsync(Computer computer, Node node) {
        if (computer == null) {
            return null;
        }
        String nodeName = getCacheKey(computer);
        boolean isOnline = computer.isOnline();
        CachedNetwork cached = NETWORK_CACHE.get(nodeName);
        if (cached != null && !cached.isExpired() && cached.onlineWhenSampled == isOnline) {
            return null;
        }

        return FleetLensTelemetryExecutor.submitDeduplicated("net:" + nodeName, () -> {
            probeAndCacheNetwork(computer, node, nodeName);
            return null;
        });
    }

    /**
     * Non-blocking resolution of the computer's hostname.
     */
    public static String resolveHostName(Computer computer, Node node) {
        if (computer == null) {
            return null;
        }
        String nodeName = getCacheKey(computer);
        boolean isOnline = computer.isOnline();

        CachedNetwork cached = NETWORK_CACHE.get(nodeName);
        if (cached != null && !cached.isExpired() && cached.onlineWhenSampled == isOnline) {
            return cached.hostName;
        }

        warmupAsync(computer, node);

        cached = NETWORK_CACHE.get(nodeName);
        if (cached != null && cached.hostName != null) {
            return cached.hostName;
        }

        // Fast non-blocking launcher host fallback
        return extractLauncherHost(resolveLauncher(computer, node));
    }

    /**
     * Resolves the verified IPv4 address for the given computer and node without blocking
     * the calling HTTP thread on slow RPCs or DNS timeouts.
     */
    public static String resolveIpAddress(Computer computer, Node node, String hostName) {
        if (computer == null) {
            return null;
        }

        String nodeName = getCacheKey(computer);
        boolean isOnline = computer.isOnline();

        // 1. Check fast literal IPv4 in explicit hostName parameter (if caller passed one directly)
        if (isIpv4(hostName)) {
            return hostName.trim();
        }

        // 2. Check positive/negative cache
        CachedNetwork cached = NETWORK_CACHE.get(nodeName);
        if (cached != null && !cached.isExpired() && cached.onlineWhenSampled == isOnline) {
            return cached.ip;
        }

        // 3. Check fast in-memory launcher host literal IPv4
        String launcherHost = extractLauncherHost(resolveLauncher(computer, node));
        if (isIpv4(launcherHost)) {
            NETWORK_CACHE.put(nodeName, new CachedNetwork(
                    hostName != null ? hostName : launcherHost,
                    launcherHost,
                    isOnline));
            return launcherHost;
        }

        // 4. Trigger background probe (RPC + DNS) if not already cached
        warmupAsync(computer, node);

        // Re-check cache in case background probe completed quickly
        cached = NETWORK_CACHE.get(nodeName);
        if (cached != null && isIpv4(cached.ip)) {
            return cached.ip;
        }

        return null;
    }

    private static void probeAndCacheNetwork(Computer computer, Node node, String nodeName) {
        boolean isOnline = computer.isOnline();
        String launcherHost = extractLauncherHost(resolveLauncher(computer, node));
        String resolvedHost = launcherHost;

        if (isOnline) {
            try {
                String chHost = computer.getHostName();
                if (chHost != null && !chHost.trim().isEmpty()) {
                    resolvedHost = chHost.trim();
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.FINE, "Failed to resolve hostname for " + nodeName, e);
            }
        }

        String resolvedIp = probeIpInternal(computer, launcherHost, resolvedHost, isOnline, nodeName);

        // Preserve last known good IP if agent went offline and offline resolution returned null
        if (resolvedIp == null && !isOnline) {
            CachedNetwork prev = NETWORK_CACHE.get(nodeName);
            if (prev != null && isIpv4(prev.ip)) {
                resolvedIp = prev.ip;
            }
        }

        NETWORK_CACHE.put(nodeName, new CachedNetwork(resolvedHost, resolvedIp, isOnline));
    }

    private static String probeIpInternal(
            Computer computer,
            String launcherHost,
            String hostName,
            boolean isOnline,
            String nodeName) {

        // 1. Probe live agent network interfaces via remoting channel
        if (isOnline) {
            VirtualChannel channel = computer.getChannel();
            if (channel != null) {
                try {
                    String probedIp = channel.call(new FetchAgentNetworkCallable());
                    if (isIpv4(probedIp)) {
                        return probedIp.trim();
                    }
                } catch (IOException | InterruptedException e) {
                    LOGGER.log(Level.FINE, "Failed to probe network interfaces for " + nodeName, e);
                }
            }

            // 2. Check environment variables (only when online)
            try {
                EnvVars env = computer.getEnvironment();
                if (env != null) {
                    String vsphereIp = env.get("VSPHERE_IP");
                    if (isIpv4(vsphereIp)) return vsphereIp.trim();

                    String ec2Private = env.get("EC2_PRIVATE_IP_ADDRESS");
                    if (isIpv4(ec2Private)) return ec2Private.trim();

                    String ec2Public = env.get("EC2_PUBLIC_IP_ADDRESS");
                    if (isIpv4(ec2Public)) return ec2Public.trim();

                    String sshConn = env.get("SSH_CONNECTION");
                    if (sshConn != null) {
                        String[] parts = sshConn.trim().split("\\s+");
                        if (parts.length >= 4 && isIpv4(parts[2])) {
                            return parts[2];
                        } else if (parts.length >= 1 && isIpv4(parts[0])) {
                            return parts[0];
                        }
                    }
                }
            } catch (IOException | InterruptedException | RuntimeException ignored) {
            }
        }

        // 3. Check launcher host literal IPv4
        if (isIpv4(launcherHost)) {
            return launcherHost.trim();
        }

        // 4. Check hostName literal IPv4
        if (isIpv4(hostName)) {
            return hostName.trim();
        }

        // 5. Attempt DNS resolution on background thread (launcherHost first, then hostName)
        String dnsCandidate = (launcherHost != null && !launcherHost.trim().isEmpty()) ? launcherHost : hostName;
        if (dnsCandidate != null && !dnsCandidate.trim().isEmpty()) {
            try {
                InetAddress addr = InetAddress.getByName(dnsCandidate.trim());
                String resolved = addr.getHostAddress();
                if (isIpv4(resolved)) {
                    return resolved;
                }
            } catch (UnknownHostException | SecurityException ignored) {
                // Unresolvable hostname; negative result will be cached in NETWORK_CACHE
            }
        }

        return null;
    }

    private static String getCacheKey(Computer computer) {
        String nodeName = computer.getName();
        return nodeName.isEmpty() ? "(controller)" : nodeName;
    }

    private static ComputerLauncher resolveLauncher(Computer computer, Node node) {
        if (computer instanceof SlaveComputer) {
            return ((SlaveComputer) computer).getLauncher();
        } else if (node instanceof Slave) {
            return ((Slave) node).getLauncher();
        }
        return null;
    }

    /**
     * Checks if a string is a valid IPv4 address in dot-decimal notation.
     */
    public static boolean isIpv4(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        String trimmed = str.trim();
        return IPV4_PATTERN.matcher(trimmed).matches() && !trimmed.startsWith("127.");
    }

    /**
     * Unwraps delegating launchers and extracts getHost() via reflection.
     */
    private static String extractLauncherHost(ComputerLauncher launcher) {
        if (launcher == null) {
            return null;
        }
        if (launcher instanceof DelegatingComputerLauncher) {
            return extractLauncherHost(((DelegatingComputerLauncher) launcher).getLauncher());
        }
        try {
            Method m = launcher.getClass().getMethod("getHost");
            Object res = m.invoke(launcher);
            if (res != null) {
                return String.valueOf(res).trim();
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
        }
        return null;
    }

    public static void clearCache(String nodeName) {
        if (nodeName != null) {
            NETWORK_CACHE.remove(nodeName);
        }
    }
}
