package io.jenkins.plugins.fleetlens.monitoring;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FleetLensComputerListenerTest {

    @Test
    void testListenerExtensionRegistered(JenkinsRule j) {
        FleetLensComputerListener listener = j.jenkins.getExtensionList(FleetLensComputerListener.class).get(0);
        assertNotNull(listener);
    }

    @Test
    void testOnOnlineAndOnOfflineCallbacks(JenkinsRule j) throws Exception {
        FleetLensComputerListener listener = j.jenkins.getExtensionList(FleetLensComputerListener.class).get(0);
        DumbSlave slave = j.createOnlineSlave();
        Computer computer = slave.toComputer();
        assertNotNull(computer);

        assertDoesNotThrow(() -> listener.onOnline(computer, TaskListener.NULL));
        assertDoesNotThrow(() -> listener.onOffline(computer, new OfflineCause.UserCause(null, "Testing offline callback")));
        assertDoesNotThrow(() -> listener.onOnline(null, TaskListener.NULL));
        assertDoesNotThrow(() -> listener.onOffline(null, null));
    }
}
