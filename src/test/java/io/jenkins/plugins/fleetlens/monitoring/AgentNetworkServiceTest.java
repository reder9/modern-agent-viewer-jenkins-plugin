package io.jenkins.plugins.fleetlens.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.slaves.DumbSlave;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class AgentNetworkServiceTest {

    @BeforeEach
    void setUp() {
        AgentNetworkService.clearCache("test-agent");
        AgentNetworkService.clearCache("win-vm-01");
    }

    @Test
    void testIsIpv4Validation() {
        assertTrue(AgentNetworkService.isIpv4("10.0.1.25"));
        assertTrue(AgentNetworkService.isIpv4("172.16.50.4"));
        assertTrue(AgentNetworkService.isIpv4("192.168.1.100"));
        assertTrue(AgentNetworkService.isIpv4("8.8.8.8"));

        // Loopback must be rejected
        assertFalse(AgentNetworkService.isIpv4("127.0.0.1"));
        assertFalse(AgentNetworkService.isIpv4("127.0.1.1"));

        // Invalid IP strings and NetBIOS names must be rejected
        assertFalse(AgentNetworkService.isIpv4("WIN-A8F432B9"));
        assertFalse(AgentNetworkService.isIpv4("WIN-SERVER-2022"));
        assertFalse(AgentNetworkService.isIpv4("agent-docker-container"));
        assertFalse(AgentNetworkService.isIpv4("256.0.0.1"));
        assertFalse(AgentNetworkService.isIpv4("192.168.1"));
        assertFalse(AgentNetworkService.isIpv4("192.168.1.1:8080"));
        assertFalse(AgentNetworkService.isIpv4(""));
        assertFalse(AgentNetworkService.isIpv4("   "));
        assertFalse(AgentNetworkService.isIpv4(null));
    }

    @Test
    void testResolveIpAddressWithValidIpHost(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createSlave("test-agent", null, null);
        String resolved = AgentNetworkService.resolveIpAddress(slave.toComputer(), slave, "192.168.10.45");
        assertEquals("192.168.10.45", resolved);
    }

    @Test
    void testResolveIpAddressRejectsUnresolvableNetBiosName(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createSlave("win-vm-01", null, null);
        // Unresolvable NetBIOS machine name should return null instead of polluting the IP field
        String resolved = AgentNetworkService.resolveIpAddress(slave.toComputer(), slave, "WIN-NONEXISTENT-VM");
        assertNull(resolved, "Unresolvable NetBIOS name must not be used as an IP address");
    }
}
