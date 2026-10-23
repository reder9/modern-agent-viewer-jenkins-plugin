package io.jenkins.plugins.fleetlens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.jenkins.plugins.fleetlens.api.dto.OsDto;
import io.jenkins.plugins.fleetlens.monitoring.AgentOsDetector;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class AgentOsDetectorTest {

    @Test
    void testDetectControllerOs(JenkinsRule j) {
        OsDto os = AgentOsDetector.detect(j.jenkins.toComputer());
        assertNotNull(os);
        assertNotNull(os.getFamily());
        assertNotNull(os.getPrettyName());
        assertNotNull(os.getArch());
        assertNotNull(os.getIcon());
    }

    @Test
    void testNullComputerFallback() {
        OsDto os = AgentOsDetector.detect(null);
        assertNotNull(os);
        assertEquals("OTHER", os.getFamily());
        assertEquals("Unknown OS", os.getPrettyName());
    }
}
