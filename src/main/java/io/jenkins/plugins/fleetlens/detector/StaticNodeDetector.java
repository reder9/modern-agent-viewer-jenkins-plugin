package io.jenkins.plugins.fleetlens.detector;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.slaves.ComputerLauncher;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;

@Extension
public class StaticNodeDetector implements AgentProvisionerDetector {

    @Override
    public int getPriority() {
        return 10; // lowest priority fallback
    }

    @Override
    public boolean isApplicable(Computer computer, Node node) {
        return true; // Catch-all fallback
    }

    @Override
    public ProvisionerDto detect(Computer computer, Node node) {
        ProvisionerDto dto = new ProvisionerDto("static", "Static Node", "symbol-server", "badge-static");
        dto.addDetail("Type", "Static / Dedicated Agent");

        if (node instanceof Slave) {
            Slave slave = (Slave) node;
            ComputerLauncher launcher = slave.getLauncher();
            if (launcher != null) {
                String launcherName = launcher.getClass().getSimpleName();
                dto.addDetail("Launcher", launcherName);
            }
        }
        return dto;
    }
}
