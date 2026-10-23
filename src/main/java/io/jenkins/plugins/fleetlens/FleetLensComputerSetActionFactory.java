package io.jenkins.plugins.fleetlens;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.ComputerSet;
import jenkins.model.TransientActionFactory;
import java.util.Collection;
import java.util.Collections;

/**
 * Attaches FleetLens action to the /computer/ page sidebar and routing.
 */
@Extension
public class FleetLensComputerSetActionFactory extends TransientActionFactory<ComputerSet> {

    @Override
    public Class<ComputerSet> type() {
        return ComputerSet.class;
    }

    @Override
    public Collection<? extends Action> createFor(ComputerSet target) {
        return Collections.singleton(new FleetLensComputerSetAction());
    }
}
