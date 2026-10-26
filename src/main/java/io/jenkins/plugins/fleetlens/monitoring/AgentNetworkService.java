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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Service for discovering and verifying agent network IP addresses.
 * Probes the agent's OS network stack over remoting channel, and checks launchers,
 * environment variables, and vSphere/EC2 metadata as fallback.
 */
public final class AgentNetworkService {

    private static final Logger LOGGER = Logger.getLogger(AgentNetworkService.class.getName());
    private static final Map<String, CachedIp> IP_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L; // 5 minutes

    private static final Pattern IPV4_PATTERN =
            Pattern.compile("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.){3}(25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)$");

    private AgentNetworkService() {}

    private static final class CachedIp {
        final String ip;
        final long timestamp;

        CachedIp(String ip) {
            this.ip = ip;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    /**
     * Resolves the verified IPv4 address for the given computer and node.
     * Returns null if no valid IP address can be found.
     */
    public static String resolveIpAddress(Computer computer, Node node, String hostName) {
        if (computer == null) {
            return null;
        }

        String nodeName = computer.getName();
        if (nodeName.isEmpty()) {
            nodeName = "(controller)";
        }

        // 1. Check cache
        CachedIp cached = IP_CACHE.get(nodeName);
        if (cached != null && !cached.isExpired() && isIpv4(cached.ip)) {
            return cached.ip;
        }

        // 2. Probing live agent network interfaces via remoting channel
        if (computer.isOnline()) {
            VirtualChannel channel = computer.getChannel();
            if (channel != null) {
                try {
                    String probedIp = channel.call(new FetchAgentNetworkCallable());
                    if (isIpv4(probedIp)) {
                        IP_CACHE.put(nodeName, new CachedIp(probedIp));
                        return probedIp;
                    }
                } catch (IOException | InterruptedException e) {
                    LOGGER.log(Level.FINE, "Failed to probe network interfaces for " + nodeName, e);
                }
            }
        }

        // 3. Check environment variables (e.g. VSPHERE_IP, EC2_PRIVATE_IP_ADDRESS, SSH_CONNECTION)
        try {
            EnvVars env = computer.getEnvironment();
            if (env != null) {
                String vsphereIp = env.get("VSPHERE_IP");
                if (isIpv4(vsphereIp)) {
                    IP_CACHE.put(nodeName, new CachedIp(vsphereIp));
                    return vsphereIp;
                }
                String ec2Private = env.get("EC2_PRIVATE_IP_ADDRESS");
                if (isIpv4(ec2Private)) {
                    IP_CACHE.put(nodeName, new CachedIp(ec2Private));
                    return ec2Private;
                }
                String ec2Public = env.get("EC2_PUBLIC_IP_ADDRESS");
                if (isIpv4(ec2Public)) {
                    IP_CACHE.put(nodeName, new CachedIp(ec2Public));
                    return ec2Public;
                }
                String sshConn = env.get("SSH_CONNECTION");
                if (sshConn != null) {
                    String[] parts = sshConn.trim().split("\\s+");
                    // SSH_CONNECTION format: <client_ip> <client_port> <server_ip> <server_port>
                    // For an inbound SSH session to the agent, server_ip (parts[2]) is the agent's IP.
                    if (parts.length >= 4 && isIpv4(parts[2])) {
                        IP_CACHE.put(nodeName, new CachedIp(parts[2]));
                        return parts[2];
                    } else if (parts.length >= 1 && isIpv4(parts[0])) {
                        IP_CACHE.put(nodeName, new CachedIp(parts[0]));
                        return parts[0];
                    }
                }
            }
        } catch (IOException | InterruptedException ignored) {
        }

        // 4. Check launcher host configuration (SSHLauncher, etc.)
        ComputerLauncher launcher = null;
        if (computer instanceof SlaveComputer) {
            launcher = ((SlaveComputer) computer).getLauncher();
        } else if (node instanceof Slave) {
            launcher = ((Slave) node).getLauncher();
        }
        String launcherHost = extractLauncherHost(launcher);
        if (isIpv4(launcherHost)) {
            IP_CACHE.put(nodeName, new CachedIp(launcherHost));
            return launcherHost;
        }

        // 5. If hostName itself is already an IPv4 address
        if (isIpv4(hostName)) {
            IP_CACHE.put(nodeName, new CachedIp(hostName));
            return hostName;
        }

        // 6. Attempt DNS resolution of hostName from controller
        if (hostName != null && !hostName.trim().isEmpty()) {
            try {
                InetAddress addr = InetAddress.getByName(hostName.trim());
                String resolved = addr.getHostAddress();
                if (isIpv4(resolved) && !resolved.startsWith("127.")) {
                    IP_CACHE.put(nodeName, new CachedIp(resolved));
                    return resolved;
                }
            } catch (UnknownHostException | SecurityException ignored) {
                // Resolution failed (e.g. unresolvable NetBIOS machine name)
            }
        }

        // If cached value exists even if expired, return as last-known good IP
        if (cached != null && isIpv4(cached.ip)) {
            return cached.ip;
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
            IP_CACHE.remove(nodeName);
        }
    }
}
