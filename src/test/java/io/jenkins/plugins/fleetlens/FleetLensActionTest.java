package io.jenkins.plugins.fleetlens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Computer;
import hudson.slaves.DumbSlave;
import io.jenkins.plugins.fleetlens.api.dto.AgentDto;
import io.jenkins.plugins.fleetlens.api.dto.FleetOverviewDto;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.net.URL;
import java.util.List;

@WithJenkins
class FleetLensActionTest {

    @Test
    void testActionRegisteredAndReachable(JenkinsRule j) throws Exception {
        FleetLensAction action = j.jenkins.getExtensionList(FleetLensAction.class).get(0);
        assertNotNull(action);
        assertEquals("agents", action.getUrlName());
        assertEquals("Modern Agent Viewer", action.getDisplayName());

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.goTo("agents");
        // Verify backwards compatible redirect
        wc.goTo("fleet");
    }

    @Test
    void testFleetOverviewWithControllerAndAgent(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createOnlineSlave();
        String slaveName = slave.getNodeName();

        FleetOverviewDto overview = FleetLensDataService.getFleetOverview();
        assertNotNull(overview);
        // Controller + 1 slave
        assertEquals(2, overview.getTotalAgents());
        assertEquals(2, overview.getOnlineAgents());
        assertEquals(0, overview.getOfflineAgents());

        List<AgentDto> agents = FleetLensDataService.getAllAgents();
        assertEquals(2, agents.size());

        AgentDto slaveDto = agents.stream()
                .filter(a -> slaveName.equals(a.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(slaveDto);
        assertTrue(slaveDto.isOnline());
        assertNotNull(slaveDto.getProvisioner());
        assertNotNull(slaveDto.getOs());
        assertNotNull(slaveDto.getStorage());
        assertNotNull(slaveDto.getTimeMetrics());
        assertTrue(slaveDto.getTimeMetrics().isConnected());
    }

    @Test
    void testToggleOfflineAction(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createOnlineSlave();
        String slaveName = slave.getNodeName();

        FleetLensAction action = j.jenkins.getExtensionList(FleetLensAction.class).get(0);
        Computer computer = slave.toComputer();
        assertNotNull(computer);
        assertFalse(computer.isTemporarilyOffline());

        // Toggle offline
        action.doToggleOffline(slaveName, true, "Upgrading system packages");
        assertTrue(computer.isTemporarilyOffline());
        assertEquals("Upgrading system packages", computer.getOfflineCauseReason());

        // Toggle back online
        action.doToggleOffline(slaveName, false, null);
        assertFalse(computer.isTemporarilyOffline());
    }

    @Test
    void testApiEndpointJsonResponse(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createOnlineSlave();
        assertNotNull(slave);

        FleetLensAction action = j.jenkins.getExtensionList(FleetLensAction.class).get(0);
        assertNotNull(action);

        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest request = new WebRequest(new URL(j.getURL() + "agents/api"), HttpMethod.GET);
        request.setAdditionalHeader("Accept", "application/json");

        WebResponse response = wc.loadWebResponse(request);
        assertEquals(200, response.getStatusCode());

        // Verify backwards-compatible endpoint as well
        WebRequest legacyReq = new WebRequest(new URL(j.getURL() + "fleet/api"), HttpMethod.GET);
        legacyReq.setAdditionalHeader("Accept", "application/json");
        WebResponse legacyResp = wc.loadWebResponse(legacyReq);
        assertEquals(200, legacyResp.getStatusCode());

        String jsonStr = response.getContentAsString();
        JSONObject root = JSONObject.fromObject(jsonStr);
        JSONObject data = root.has("data") ? root.getJSONObject("data") : root;

        assertTrue(data.has("overview"), "JSON should have overview: " + jsonStr);
        assertTrue(data.has("agents"), "JSON should have agents: " + jsonStr);

        JSONObject overview = data.getJSONObject("overview");
        int totalAgents = overview.getInt("totalAgents");
        assertTrue(totalAgents >= 2, "Expected at least 2 agents in overview (found: " + totalAgents + ") JSON: " + jsonStr);
    }

    @Test
    void testExecuteDiagnosticValidation(JenkinsRule j) throws Exception {
        FleetLensAction action = j.jenkins.getExtensionList(FleetLensAction.class).get(0);
        assertNotNull(action);

        // Missing agent name
        org.kohsuke.stapler.HttpResponse r1 = action.doExecuteDiagnostic(null, "echo 1", 10);
        assertNotNull(r1);

        // Non-existent agent
        org.kohsuke.stapler.HttpResponse r2 = action.doExecuteDiagnostic("ghost-node", "echo 1", 10);
        assertNotNull(r2);

        // Empty command
        org.kohsuke.stapler.HttpResponse r3 = action.doExecuteDiagnostic("(built-in)", "", 10);
        assertNotNull(r3);

        // Clear cache
        org.kohsuke.stapler.HttpResponse r4 = action.doClearCache("(built-in)");
        assertNotNull(r4);
    }
}
