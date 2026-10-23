package io.jenkins.plugins.fleetlens;

import hudson.Extension;
import hudson.model.RootAction;
import jakarta.servlet.ServletException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.GET;

import java.io.IOException;

/**
 * Backwards-compatibility root action forwarding legacy /fleet requests to /agents.
 */
@Extension
public class FleetRedirectAction implements RootAction {

    @Override
    public String getIconFileName() {
        return null; // Suppressed from left navigation sidebar
    }

    @Override
    public String getDisplayName() {
        return null; // Suppressed from left navigation sidebar
    }

    @Override
    public String getUrlName() {
        return "fleet";
    }

    @GET
    public void doIndex(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        String queryString = req.getQueryString();
        String target = req.getContextPath() + "/agents" + (queryString != null && !queryString.isEmpty() ? "?" + queryString : "");
        rsp.sendRedirect2(target);
    }

    public Object getDynamic(String token, StaplerRequest2 req, StaplerResponse2 rsp) {
        return Jenkins.get().getExtensionList(FleetLensAction.class).get(0);
    }
}
