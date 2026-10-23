package io.jenkins.plugins.fleetlens;

import hudson.model.Action;
import hudson.model.Computer;
import jakarta.servlet.ServletException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.GET;

import java.io.IOException;

/**
 * Action attached to each Computer, linking to FleetLens modern view.
 */
public class FleetLensComputerAction implements Action {

    private final Computer computer;

    public FleetLensComputerAction(Computer computer) {
        this.computer = computer;
    }

    public Computer getComputer() {
        return computer;
    }

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
        String compUrl = computer.getUrl();
        if (compUrl == null || compUrl.isEmpty()) {
            String rawName = computer.getName();
            compUrl = "computer/" + (rawName.isEmpty() ? "(built-in)" : rawName) + "/";
        }
        rsp.sendRedirect2(req.getContextPath() + "/" + compUrl + "?view=modern");
    }
}
