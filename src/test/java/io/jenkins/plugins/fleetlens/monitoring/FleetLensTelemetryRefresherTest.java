package io.jenkins.plugins.fleetlens.monitoring;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.TaskListener;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FleetLensTelemetryRefresherTest {

    @Test
    void testRefresherExtensionRegistered(JenkinsRule j) {
        FleetLensTelemetryRefresher refresher = j.jenkins.getExtensionList(FleetLensTelemetryRefresher.class).get(0);
        assertNotNull(refresher);
        assertEquals(TimeUnit.MINUTES.toMillis(2), refresher.getRecurrencePeriod());
    }

    @Test
    void testRefresherExecution(JenkinsRule j) {
        FleetLensTelemetryRefresher refresher = j.jenkins.getExtensionList(FleetLensTelemetryRefresher.class).get(0);
        assertDoesNotThrow(() -> refresher.execute(TaskListener.NULL));
    }
}
