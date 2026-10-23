package io.jenkins.plugins.fleetlens;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Computer;
import jenkins.model.TransientActionFactory;

import java.util.Collection;
import java.util.Collections;

/**
 * Attaches FleetLens action to each individual Computer sidebar and routing.
 */
@Extension
public class FleetLensComputerActionFactory extends TransientActionFactory<Computer> {

    @Override
    public Class<Computer> type() {
        return Computer.class;
    }

    @Override
    public Collection<? extends Action> createFor(Computer target) {
        if (target == null) {
            return Collections.emptyList();
        }
        return Collections.singleton(new FleetLensComputerAction(target));
    }
}
