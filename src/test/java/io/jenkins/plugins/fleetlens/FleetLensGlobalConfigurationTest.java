package io.jenkins.plugins.fleetlens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.util.FormValidation;
import io.jenkins.plugins.fleetlens.configuration.FleetLensGlobalConfiguration;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FleetLensGlobalConfigurationTest {

    @Test
    void testConfigurationDefaultsAndBoundaries(JenkinsRule j) {
        FleetLensGlobalConfiguration config = FleetLensGlobalConfiguration.get();
        assertNotNull(config);

        // Verify standard defaults
        assertEquals(78, config.getStorageWarningPercent());
        assertEquals(90, config.getStorageCriticalPercent());
        assertEquals(15, config.getAutoRefreshSeconds());
        assertEquals("cards", config.getDefaultViewMode());
        assertFalse(config.isRedirectComputerSet());
        assertTrue(config.isModernizeComputerPages());

        // Test boundary clamping
        config.setStorageWarningPercent(5); // below 10 -> clamped to 10
        assertEquals(10, config.getStorageWarningPercent());

        config.setStorageWarningPercent(105); // above 99 -> clamped to 99
        assertEquals(99, config.getStorageWarningPercent());

        config.setStorageCriticalPercent(150); // above 100 -> clamped to 100
        assertEquals(100, config.getStorageCriticalPercent());

        // Restore defaults
        config.setStorageWarningPercent(78);
        config.setStorageCriticalPercent(90);
    }

    @Test
    void testFormValidation(JenkinsRule j) {
        FleetLensGlobalConfiguration config = FleetLensGlobalConfiguration.get();

        assertEquals(FormValidation.Kind.OK, config.doCheckStorageWarningPercent(78).kind);
        assertEquals(FormValidation.Kind.ERROR, config.doCheckStorageWarningPercent(5).kind);
        assertEquals(FormValidation.Kind.ERROR, config.doCheckStorageWarningPercent(100).kind);

        assertEquals(FormValidation.Kind.OK, config.doCheckStorageCriticalPercent(90).kind);
        assertEquals(FormValidation.Kind.ERROR, config.doCheckStorageCriticalPercent(5).kind);
        assertEquals(FormValidation.Kind.ERROR, config.doCheckStorageCriticalPercent(105).kind);
    }
}
