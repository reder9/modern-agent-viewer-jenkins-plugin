package io.jenkins.plugins.fleetlens.monitoring.callable;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import jenkins.security.MasterToSlaveCallable;

/**
 * Callable executed on an agent channel to discover its bound IPv4 network addresses directly
 * from the operating system network stack, bypassing DNS and NetBIOS hostname resolution.
 */
public class FetchAgentNetworkCallable extends MasterToSlaveCallable<String, IOException> {

    private static final long serialVersionUID = 1L;

    @Override
    public String call() throws IOException {
        List<Inet4Address> siteLocalAddrs = new ArrayList<>();
        List<Inet4Address> otherAddrs = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface iface = interfaces.nextElement();
                    try {
                        if (iface.isLoopback() || !iface.isUp()) {
                            continue;
                        }
                    } catch (SocketException ignored) {
                        continue;
                    }

                    String ifaceName = iface.getName().toLowerCase(Locale.ROOT);
                    // Filter out virtual container bridges if the agent host also runs container daemons
                    if (ifaceName.startsWith("veth") || ifaceName.startsWith("docker") || ifaceName.startsWith("br-")) {
                        continue;
                    }

                    Enumeration<InetAddress> addrs = iface.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                            Inet4Address v4 = (Inet4Address) addr;
                            if (v4.isSiteLocalAddress()) {
                                siteLocalAddrs.add(v4);
                            } else {
                                otherAddrs.add(v4);
                            }
                        }
                    }
                }
            }
        } catch (SocketException ignored) {
        }

        // Return first site-local private IPv4 address (10.x, 172.16-31.x, 192.168.x)
        if (!siteLocalAddrs.isEmpty()) {
            return siteLocalAddrs.get(0).getHostAddress();
        }

        // Next, return any other routable public IPv4 address
        if (!otherAddrs.isEmpty()) {
            return otherAddrs.get(0).getHostAddress();
        }

        // Fallback: InetAddress.getLocalHost() if it provides a valid non-loopback IPv4
        try {
            InetAddress local = InetAddress.getLocalHost();
            if (local instanceof Inet4Address && !local.isLoopbackAddress() && !local.isLinkLocalAddress()) {
                return local.getHostAddress();
            }
        } catch (UnknownHostException ignored) {
        }

        return null;
    }
}
