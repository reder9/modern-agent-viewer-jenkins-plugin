package io.jenkins.plugins.fleetlens.configuration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.util.FormValidation;
import io.jenkins.plugins.fleetlens.monitoring.AgentStorageService;
import io.jenkins.plugins.fleetlens.monitoring.AgentUptimeService;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Global configuration settings for FleetLens modern agent viewer.
 */
@Extension
@Symbol({"modernAgentViewer", "fleetLens"})
public class FleetLensGlobalConfiguration extends GlobalConfiguration {

    private boolean redirectComputerSet = false;
    private boolean modernizeComputerPages = true;
    private int storageWarningPercent = 78;
    private int storageCriticalPercent = 90;
    private int autoRefreshSeconds = 15;
    private String defaultViewMode = "cards"; // "cards" or "table"

    @SuppressFBWarnings(value = "MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR", justification = "Standard Jenkins GlobalConfiguration idiom")
    public FleetLensGlobalConfiguration() {
        load();
    }

    public static FleetLensGlobalConfiguration get() {
        return GlobalConfiguration.all().get(FleetLensGlobalConfiguration.class);
    }

    public boolean isRedirectComputerSet() {
        return redirectComputerSet;
    }

    @DataBoundSetter
    public void setRedirectComputerSet(boolean redirectComputerSet) {
        this.redirectComputerSet = redirectComputerSet;
        save();
    }

    public boolean isModernizeComputerPages() {
        return modernizeComputerPages;
    }

    @DataBoundSetter
    public void setModernizeComputerPages(boolean modernizeComputerPages) {
        this.modernizeComputerPages = modernizeComputerPages;
        save();
    }

    public int getStorageWarningPercent() {
        return storageWarningPercent;
    }

    @DataBoundSetter
    public void setStorageWarningPercent(int storageWarningPercent) {
        this.storageWarningPercent = Math.max(10, Math.min(99, storageWarningPercent));
        save();
    }

    public int getStorageCriticalPercent() {
        return storageCriticalPercent;
    }

    @DataBoundSetter
    public void setStorageCriticalPercent(int storageCriticalPercent) {
        this.storageCriticalPercent = Math.max(10, Math.min(100, storageCriticalPercent));
        save();
    }

    public int getAutoRefreshSeconds() {
        return autoRefreshSeconds;
    }

    @DataBoundSetter
    public void setAutoRefreshSeconds(int autoRefreshSeconds) {
        this.autoRefreshSeconds = autoRefreshSeconds;
        save();
    }

    public String getDefaultViewMode() {
        return defaultViewMode != null ? defaultViewMode : "cards";
    }

    @DataBoundSetter
    public void setDefaultViewMode(String defaultViewMode) {
        this.defaultViewMode = defaultViewMode != null ? defaultViewMode : "cards";
        save();
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        save();
        return true;
    }

    public FormValidation doCheckStorageWarningPercent(@QueryParameter int value) {
        if (value < 10 || value > 99) {
            return FormValidation.error("Warning percentage must be between 10% and 99%");
        }
        return FormValidation.ok();
    }

    public FormValidation doCheckStorageCriticalPercent(@QueryParameter int value) {
        if (value < 10 || value > 100) {
            return FormValidation.error("Critical percentage must be between 10% and 100%");
        }
        return FormValidation.ok();
    }
}
