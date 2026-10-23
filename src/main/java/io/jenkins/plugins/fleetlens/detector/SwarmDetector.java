package io.jenkins.plugins.fleetlens.detector;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;
import java.lang.reflect.Method;
import java.util.Locale;

@Extension
public class SwarmDetector implements AgentProvisionerDetector {

    @Override
    public int getPriority() {
        return 85;
    }

    @Override
    public boolean isApplicable(Computer computer, Node node) {
        if (computer != null) {
            String compClass = computer.getClass().getName().toLowerCase(Locale.ROOT);
            if (compClass.contains("swarm")) {
                return true;
            }
        }
        if (node != null) {
            String className = node.getClass().getName().toLowerCase(Locale.ROOT);
            if (className.contains("swarm") || className.contains("swarmslave")) {
                return true;
            }
            String nodeDesc = node.getNodeDescription();
            if (nodeDesc != null) {
                String descLower = nodeDesc.toLowerCase(Locale.ROOT);
                if (descLower.contains("swarm client") || descLower.contains("jenkins swarm")) {
                    return true;
                }
            }
            String nodeName = node.getNodeName().toLowerCase(Locale.ROOT);
            if (nodeName.startsWith("swarm-")) {
                return true;
            }
            for (Label l : node.getAssignedLabels()) {
                String lname = l.getName().toLowerCase(Locale.ROOT);
                if (lname.equals("swarm") || lname.contains("swarm-")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public ProvisionerDto detect(Computer computer, Node node) {
        ProvisionerDto dto = new ProvisionerDto("swarm", "Jenkins Swarm", "symbol-swarm", "badge-swarm");
        dto.addDetail("Type", "Auto-Discovered Swarm Agent");

        if (node != null) {
            tryReflectiveMethod(node, "getRemoteFs", "Remote FS", dto);
            tryReflectiveMethod(node, "isAutoDiscovery", "Auto-Discovery", dto);
            tryReflectiveMethod(node, "getSwarmVersion", "Swarm Client Version", dto);
        }
        if (computer != null) {
            tryReflectiveMethod(computer, "getSwarmVersion", "Swarm Client Version", dto);
        }
        return dto;
    }

    private void tryReflectiveMethod(Object target, String methodName, String detailKey, ProvisionerDto dto) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object res = m.invoke(target);
            if (res != null) {
                String val = String.valueOf(res).trim();
                if (!val.isEmpty() && !dto.getDetails().containsKey(detailKey)) {
                    dto.addDetail(detailKey, val);
                }
            }
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException ignored) {
            // Method not present or failed
        }
    }
}
