package io.jenkins.plugins.fleetlens;

import hudson.model.Action;
import hudson.util.HttpResponses;
import jakarta.servlet.ServletException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.GET;

import java.io.IOException;

/**
 * Action attached to ComputerSet, making FleetLens accessible directly via /computer/fleet.
 */
public class FleetLensComputerSetAction implements Action {

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

    @GET
    public void doIndex(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.READ);
        // Forward directly to /agents
        rsp.sendRedirect2(req.getContextPath() + "/agents");
    }
}
