package io.jenkins.plugins.fleetlens.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.fleetlens.api.FleetJsonSerializer;
import io.jenkins.plugins.fleetlens.api.dto.AgentDto;
import io.jenkins.plugins.fleetlens.api.dto.ConnectionDto;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;

class AgentConnectionServiceTest {

    @Test
    void testInferUserFromPath() {
        // macOS patterns
        assertEquals("jenkins", AgentConnectionService.inferUserFromPath("/Users/jenkins/agent"));
        assertEquals("admin", AgentConnectionService.inferUserFromPath("/Users/admin"));
        assertEquals("caleb.reder", AgentConnectionService.inferUserFromPath("/Users/caleb.reder/workspace"));

        // Linux patterns
        assertEquals("ubuntu", AgentConnectionService.inferUserFromPath("/home/ubuntu/jenkins"));
        assertEquals("ec2-user", AgentConnectionService.inferUserFromPath("/home/ec2-user/agent"));
        assertEquals("jenkins_runner", AgentConnectionService.inferUserFromPath("/home/jenkins_runner/ws"));

        // Windows patterns
        assertEquals("Administrator", AgentConnectionService.inferUserFromPath("C:\\Users\\Administrator\\jenkins"));
        assertEquals("builder", AgentConnectionService.inferUserFromPath("C:/Users/builder/agent"));
        assertEquals("jenkins", AgentConnectionService.inferUserFromPath("D:\\Users\\jenkins\\workspace"));

        // Non-user directory patterns
        assertNull(AgentConnectionService.inferUserFromPath("/var/jenkins_home"));
        assertNull(AgentConnectionService.inferUserFromPath("D:\\jenkins-agent"));
        assertNull(AgentConnectionService.inferUserFromPath("/opt/agent"));
        assertNull(AgentConnectionService.inferUserFromPath(null));
        assertNull(AgentConnectionService.inferUserFromPath(""));
    }

    @Test
    void testConnectionDtoSerialization() {
        AgentDto agent = new AgentDto();
        agent.setName("test-ssh-agent");

        ConnectionDto conn = new ConnectionDto();
        conn.setSshUser("ec2-user");
        conn.setSshHost("10.0.1.55");
        conn.setSshPort(2222);
        conn.setUserResolutionSource("SSH Credentials (StandardUsernameCredentials)");
        conn.setJnlpSecret("mock-hmac-secret");
        conn.setPrimaryCommand("ssh -p 2222 ec2-user@10.0.1.55");
        conn.setPrimaryCommandType("SSH");

        agent.setConnectionInfo(conn);

        JSONObject json = FleetJsonSerializer.serializeAgent(agent, true);
        assertTrue(json.has("connectionInfo"));

        JSONObject cj = json.getJSONObject("connectionInfo");
        assertEquals("ec2-user", cj.getString("sshUser"));
        assertEquals("10.0.1.55", cj.getString("sshHost"));
        assertEquals(2222, cj.getInt("sshPort"));
        assertEquals("SSH Credentials (StandardUsernameCredentials)", cj.getString("userResolutionSource"));
        assertEquals("mock-hmac-secret", cj.getString("jnlpSecret"));
        assertEquals("ssh -p 2222 ec2-user@10.0.1.55", cj.getString("primaryCommand"));
        assertEquals("SSH", cj.getString("primaryCommandType"));
    }
}
