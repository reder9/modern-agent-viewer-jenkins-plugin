package io.jenkins.plugins.fleetlens;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.RootAction;
import hudson.model.User;
import hudson.slaves.OfflineCause;
import hudson.util.HttpResponses;
import io.jenkins.plugins.fleetlens.api.FleetJsonSerializer;
import io.jenkins.plugins.fleetlens.api.dto.AgentDto;
import io.jenkins.plugins.fleetlens.api.dto.FleetOverviewDto;
import io.jenkins.plugins.fleetlens.api.dto.QueueOverviewDto;
import io.jenkins.plugins.fleetlens.configuration.FleetLensGlobalConfiguration;
import io.jenkins.plugins.fleetlens.monitoring.AgentOsDetector;
import io.jenkins.plugins.fleetlens.monitoring.AgentStorageService;
import io.jenkins.plugins.fleetlens.monitoring.AgentUptimeService;
import io.jenkins.plugins.fleetlens.queue.QueueInspectionService;
import jakarta.servlet.ServletException;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.GET;

import hudson.remoting.VirtualChannel;
import io.jenkins.plugins.fleetlens.monitoring.callable.DiagnosticResult;
import io.jenkins.plugins.fleetlens.monitoring.callable.ExecuteDiagnosticCommandCallable;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Root action exposing the FleetLens modern agent dashboard and REST API.
 */
@Extension
public class FleetLensAction implements RootAction {

    private static final Logger LOGGER = Logger.getLogger(FleetLensAction.class.getName());

    @Override
    public String getIconFileName() {
        return "symbol-hardware-chip-outline plugin-ionicons-api";
    }

    @Override
    public String getDisplayName() {
        return "Modern Agent Viewer";
    }

    @Override
    public String getUrlName() {
        return "agents";
    }

    public FleetLensGlobalConfiguration getConfiguration() {
        return FleetLensGlobalConfiguration.get();
    }

    public String getInitialStateJson() {
        Jenkins.get().checkPermission(Jenkins.READ);
        boolean canConfigure = Jenkins.get().hasPermission(Jenkins.ADMINISTER) || Jenkins.get().hasPermission(Computer.CONFIGURE);

        FleetOverviewDto overview = FleetLensDataService.getFleetOverview();
        List<AgentDto> agents = FleetLensDataService.getAllAgents();
        QueueOverviewDto queue = QueueInspectionService.inspectQueue();

        JSONObject response = new JSONObject();
        response.put("overview", FleetJsonSerializer.serializeOverview(overview));
        response.put("agents", FleetJsonSerializer.serializeAgents(agents, canConfigure));
        response.put("queue", FleetJsonSerializer.serializeQueue(queue));
        response.put("defaultViewMode", getConfiguration().getDefaultViewMode());
        response.put("autoRefreshSeconds", getConfiguration().getAutoRefreshSeconds());

        return response.toString();
    }

    @GET
    public void doIndex(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.READ);
        req.getView(this, "index.jelly").forward(req, rsp);
    }

    @GET
    public HttpResponse doApi() {
        Jenkins.get().checkPermission(Jenkins.READ);
        boolean canConfigure = Jenkins.get().hasPermission(Jenkins.ADMINISTER) || Jenkins.get().hasPermission(Computer.CONFIGURE);

        FleetOverviewDto overview = FleetLensDataService.getFleetOverview();
        List<AgentDto> agents = FleetLensDataService.getAllAgents();
        QueueOverviewDto queue = QueueInspectionService.inspectQueue();

        JSONObject response = new JSONObject();
        response.put("overview", FleetJsonSerializer.serializeOverview(overview));
        response.put("agents", FleetJsonSerializer.serializeAgents(agents, canConfigure));
        response.put("queue", FleetJsonSerializer.serializeQueue(queue));
        response.put("defaultViewMode", getConfiguration().getDefaultViewMode());
        response.put("autoRefreshSeconds", getConfiguration().getAutoRefreshSeconds());

        return HttpResponses.okJSON(response);
    }

    @GET
    public HttpResponse doAgent(@QueryParameter String name) {
        Jenkins.get().checkPermission(Jenkins.READ);
        if (name == null || name.trim().isEmpty()) {
            return HttpResponses.errorJSON("Agent name must be specified");
        }

        Computer computer = findComputer(name);
        if (computer == null) {
            return HttpResponses.errorJSON("Agent not found: " + name);
        }

        boolean canConfigure = computer.hasPermission(Computer.CONFIGURE) || computer.hasPermission(Computer.DISCONNECT);
        AgentDto agent = FleetLensDataService.toAgentDto(computer);

        return HttpResponses.okJSON(FleetJsonSerializer.serializeAgent(agent, canConfigure));
    }

    @RequirePOST
    public HttpResponse doToggleOffline(@QueryParameter String name,
                                        @QueryParameter boolean offline,
                                        @QueryParameter String reason) {
        Computer computer = findComputer(name);
        if (computer == null) {
            return HttpResponses.errorJSON("Agent not found: " + name);
        }

        computer.checkPermission(Computer.DISCONNECT);

        if (offline) {
            String cause = (reason != null && !reason.trim().isEmpty())
                    ? reason.trim()
                    : "Marked offline via Modern Agent Viewer";
            computer.setTemporarilyOffline(true, new OfflineCause.UserCause(User.current(), cause));
        } else {
            computer.setTemporarilyOffline(false, null);
        }

        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("name", name);
        result.put("temporarilyOffline", computer.isTemporarilyOffline());
        result.put("status", computer.isTemporarilyOffline() ? "TEMPORARILY_OFFLINE" : (computer.isOnline() ? "ONLINE" : "OFFLINE"));

        return HttpResponses.okJSON(result);
    }

    @RequirePOST
    public HttpResponse doClearCache(@QueryParameter String name) {
        Jenkins.get().checkPermission(Jenkins.READ);
        if (name != null && !name.trim().isEmpty()) {
            AgentStorageService.clearCache(name);
            AgentOsDetector.clearCache(name);
            AgentUptimeService.clearCache(name);
        }
        JSONObject result = new JSONObject();
        result.put("success", true);
        return HttpResponses.okJSON(result);
    }

    @RequirePOST
    public HttpResponse doCancelQueue(@QueryParameter long id) {
        Jenkins.get().checkPermission(Jenkins.READ);
        boolean success = QueueInspectionService.cancelItem(id);
        JSONObject result = new JSONObject();
        result.put("success", success);
        result.put("id", id);
        return HttpResponses.okJSON(result);
    }

    @RequirePOST
    public HttpResponse doExecuteDiagnostic(@QueryParameter String name,
                                            @QueryParameter String command,
                                            @QueryParameter int timeout) {
        if (name == null || name.trim().isEmpty()) {
            return HttpResponses.errorJSON("Agent name must be specified.");
        }
        Computer computer = findComputer(name);
        if (computer == null) {
            return HttpResponses.errorJSON("Agent not found: " + name);
        }

        // Security check: Administrator or Computer.CONFIGURE
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER) && !computer.hasPermission(Computer.CONFIGURE)) {
            return HttpResponses.errorJSON("Access denied: Administrative or Agent Configure permission required for remote diagnostics.");
        }

        if (command == null || command.trim().isEmpty()) {
            return HttpResponses.errorJSON("Command cannot be empty.");
        }

        if (!computer.isOnline()) {
            return HttpResponses.errorJSON("Cannot execute command: agent '" + name + "' is currently offline.");
        }

        User current = User.current();
        String userId = current != null ? current.getId() : "anonymous";
        LOGGER.warning("Modern Agent Viewer Diagnostic Console: User '" + userId + "' executed command on node '" + name + "': " + command);

        Boolean isUnix = computer.isUnix();
        boolean isWindows = (isUnix != null && !isUnix);
        int timeoutSec = timeout > 0 ? Math.min(timeout, 60) : 15;

        VirtualChannel channel = computer.getChannel();
        try {
            DiagnosticResult result;
            if (channel != null) {
                result = channel.call(new ExecuteDiagnosticCommandCallable(command.trim(), timeoutSec, isWindows));
            } else if ("(built-in)".equalsIgnoreCase(name) || "(controller)".equalsIgnoreCase(name) || "master".equalsIgnoreCase(name)) {
                result = new ExecuteDiagnosticCommandCallable(command.trim(), timeoutSec, isWindows).call();
            } else {
                return HttpResponses.errorJSON("Agent remoting channel is unavailable.");
            }

            JSONObject json = new JSONObject();
            json.put("status", "ok");
            json.put("name", name);
            json.put("command", result.getCommand());
            json.put("exitCode", result.getExitCode());
            json.put("output", result.getOutput());
            json.put("durationMs", result.getDurationMs());
            json.put("timedOut", result.isTimedOut());
            json.put("executedAt", System.currentTimeMillis());
            return HttpResponses.okJSON(json);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Diagnostic execution failed on agent " + name, e);
            return HttpResponses.errorJSON("Diagnostic execution error: " + e.getMessage());
        }
    }

    private Computer findComputer(String name) {
        Jenkins j = Jenkins.get();
        if ("(built-in)".equalsIgnoreCase(name) || "(controller)".equalsIgnoreCase(name) || "master".equalsIgnoreCase(name)) {
            return j.toComputer();
        }
        for (Computer c : j.getComputers()) {
            if (c.getName().equalsIgnoreCase(name) || c.getDisplayName().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }
}
