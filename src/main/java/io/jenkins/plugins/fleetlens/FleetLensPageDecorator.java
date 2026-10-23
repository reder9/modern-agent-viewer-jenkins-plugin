package io.jenkins.plugins.fleetlens;

import hudson.Extension;
import hudson.model.PageDecorator;
import io.jenkins.plugins.fleetlens.configuration.FleetLensGlobalConfiguration;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Decorates the classic /computer/ page with a modern banner and handles optional auto-redirection.
 */
@Extension
public class FleetLensPageDecorator extends PageDecorator {

    public boolean isComputerPage() {
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req == null) return false;
        String uri = req.getRequestURI();
        return uri != null && (uri.endsWith("/computer") || uri.endsWith("/computer/"));
    }

    public boolean isIndividualComputerPage() {
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req == null) return false;
        String uri = req.getRequestURI();
        if (uri == null) return false;
        return uri.matches(".*/computer/[^/]+/?$")
                && !uri.endsWith("/computer/")
                && !uri.endsWith("/computer/new")
                && !uri.endsWith("/computer/new/");
    }

    public String getCurrentComputerName() {
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req == null) return null;
        String uri = req.getRequestURI();
        if (uri == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(".*/computer/([^/]+)/?$").matcher(uri);
        if (m.find()) {
            try {
                return java.net.URLDecoder.decode(m.group(1), java.nio.charset.StandardCharsets.UTF_8.name());
            } catch (java.io.UnsupportedEncodingException e) {
                return m.group(1);
            }
        }
        return null;
    }

    public boolean isModernizeComputerPages() {
        return FleetLensGlobalConfiguration.get().isModernizeComputerPages();
    }

    public boolean shouldRedirect() {
        if (!isComputerPage()) return false;
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req != null && "true".equalsIgnoreCase(req.getParameter("legacy"))) {
            return false;
        }
        return FleetLensGlobalConfiguration.get().isRedirectComputerSet();
    }
}
