package io.jenkins.plugins.fleetlens.detector;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;
import java.lang.reflect.Method;
import java.util.Locale;

@Extension
public class DockerDetector implements AgentProvisionerDetector {

    @Override
    public int getPriority() {
        return 85;
    }

    @Override
    public boolean isApplicable(Computer computer, Node node) {
        if (computer != null && computer.getClass().getName().toLowerCase(Locale.ROOT).contains("docker")) {
            return true;
        }
        if (node != null) {
            String className = node.getClass().getName().toLowerCase(Locale.ROOT);
            if (className.contains("docker")) {
                return true;
            }
            String nodeDesc = node.getNodeDescription();
            if (nodeDesc != null && nodeDesc.toLowerCase(Locale.ROOT).contains("docker")) {
                return true;
            }
            String nodeName = node.getNodeName().toLowerCase(Locale.ROOT);
            if (nodeName.contains("docker") || nodeName.contains("container")) {
                return true;
            }
            for (hudson.model.Label l : node.getAssignedLabels()) {
                String lname = l.getName().toLowerCase(Locale.ROOT);
                if (lname.equals("docker") || lname.contains("docker-") || lname.contains("-docker")) {
                    return true;
                }
            }
        }
        if (computer != null) {
            String compName = computer.getName().toLowerCase(Locale.ROOT);
            if (compName.contains("docker") || compName.contains("container")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ProvisionerDto detect(Computer computer, Node node) {
        ProvisionerDto dto = new ProvisionerDto("docker", "Docker Container", "symbol-docker", "badge-docker");
        dto.addDetail("Type", "Docker Dynamic Agent");

        if (node != null) {
            // Attempt to extract container ID or image via reflection if available
            tryReflectiveMethod(node, "getContainerId", "Container ID", dto);
            tryReflectiveMethod(node, "getImage", "Image", dto);
            tryReflectiveMethod(node, "getDockerTemplate", "Template", dto);
            tryReflectiveMethod(node, "getCloudName", "Cloud Name", dto);
        }
        if (computer != null) {
            tryReflectiveMethod(computer, "getContainerId", "Container ID", dto);
            tryReflectiveMethod(computer, "getCloudId", "Cloud Name", dto);
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
