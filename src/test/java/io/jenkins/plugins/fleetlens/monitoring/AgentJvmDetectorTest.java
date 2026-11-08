package io.jenkins.plugins.fleetlens.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.fleetlens.api.FleetJsonSerializer;
import io.jenkins.plugins.fleetlens.api.dto.AgentDto;
import io.jenkins.plugins.fleetlens.api.dto.JvmDto;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;

class AgentJvmDetectorTest {

    @Test
    void testParseMajorVersion() {
        assertEquals(21, AgentJvmDetector.parseMajorVersion("21.0.5"));
        assertEquals(21, AgentJvmDetector.parseMajorVersion("21.0.5+11-LTS"));
        assertEquals(17, AgentJvmDetector.parseMajorVersion("17.0.10"));
        assertEquals(17, AgentJvmDetector.parseMajorVersion("17.0.10-internal"));
        assertEquals(11, AgentJvmDetector.parseMajorVersion("11.0.22"));
        assertEquals(8, AgentJvmDetector.parseMajorVersion("1.8.0_382"));
        assertEquals(7, AgentJvmDetector.parseMajorVersion("1.7.0_80"));

        assertEquals(-1, AgentJvmDetector.parseMajorVersion(null));
        assertEquals(-1, AgentJvmDetector.parseMajorVersion(""));
        assertEquals(-1, AgentJvmDetector.parseMajorVersion("unknown-build"));
    }

    @Test
    void testControllerMajorVersion() {
        int ctrlMajor = AgentJvmDetector.getControllerMajorVersion();
        assertTrue(ctrlMajor >= 11, "Controller major version should be at least 11, was: " + ctrlMajor);
    }

    @Test
    void testDetectNullComputer() {
        assertNull(AgentJvmDetector.detect(null));
        assertNull(AgentJvmDetector.warmupAsync(null));
    }

    @Test
    void testJvmSerializationAndMismatch() {
        AgentDto agent = new AgentDto();
        agent.setName("agent-with-java-17");

        int ctrlMajor = AgentJvmDetector.getControllerMajorVersion();
        boolean mismatch = (ctrlMajor != 17);
        String warning = mismatch ? "Agent JVM (Java 17) does not match controller JVM (Java " + ctrlMajor + ")" : null;

        JvmDto jvm = new JvmDto("17.0.10", 17, ctrlMajor, mismatch, warning);
        agent.setJvm(jvm);
        agent.setClockDifferenceMs(6500L); // 6.5s drift

        JSONObject json = FleetJsonSerializer.serializeAgent(agent, true);
        assertTrue(json.has("jvm"));

        JSONObject jj = json.getJSONObject("jvm");
        assertEquals("17.0.10", jj.getString("version"));
        assertEquals(17, jj.getInt("majorVersion"));
        assertEquals(ctrlMajor, jj.getInt("controllerMajorVersion"));
        assertEquals(mismatch, jj.getBoolean("versionMismatch"));

        assertTrue(json.has("clockDifferenceMs"));
        assertEquals(6500L, json.getLong("clockDifferenceMs"));
    }

    @Test
    void testClearCache() {
        AgentJvmDetector.clearCache("test-node");
        AgentJvmDetector.clearCache("");
        AgentJvmDetector.clearCache(null);
    }
}
