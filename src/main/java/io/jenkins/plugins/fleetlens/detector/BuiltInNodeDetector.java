package io.jenkins.plugins.fleetlens.detector;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;
import jenkins.model.Jenkins;

@Extension
public class BuiltInNodeDetector implements AgentProvisionerDetector {

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean isApplicable(Computer computer, Node node) {
        if (computer instanceof Jenkins.MasterComputer) {
            return true;
        }
        if (computer != null && (computer.getName().isEmpty() || "(built-in)".equalsIgnoreCase(computer.getName()))) {
            return true;
        }
        Jenkins j = Jenkins.getInstanceOrNull();
        return j != null && node == j;
    }

    @Override
    public ProvisionerDto detect(Computer computer, Node node) {
        ProvisionerDto dto = new ProvisionerDto("controller", "Controller (Built-in)", "symbol-jenkins", "badge-controller");
        dto.addDetail("Role", "Jenkins Controller / Built-in Node");
        if (computer != null) {
            dto.addDetail("Executors", String.valueOf(computer.getNumExecutors()));
        }
        return dto;
    }
}
