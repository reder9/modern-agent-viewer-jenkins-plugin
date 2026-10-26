package io.jenkins.plugins.fleetlens.detector;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Detects agents provisioned by Jenkins Docker plugins (docker-plugin, yet-another-docker-plugin).
 * Prioritized below specific VM hypervisors (vSphere, Proxmox) and container orchestrators (Nomad, Kubernetes)
 * to prevent false positives on VMs with Docker installed or Nomad/Kubernetes containers.
 */
@Extension
public class DockerDetector implements AgentProvisionerDetector {

    @Override
    public int getPriority() {
        return 70; // Lower than VM hypervisors (vSphere: 90) and orchestrators (Nomad: 92, K8s: 95)
    }

    @Override
    public boolean isApplicable(Computer computer, Node node) {
        if (computer != null) {
            String compClass = computer.getClass().getName().toLowerCase(Locale.ROOT);
            if (compClass.contains("docker")) {
                return true;
            }
            if (hasMethodValue(computer, "getContainerId")) {
                return true;
            }
        }

        if (node != null) {
            String className = node.getClass().getName().toLowerCase(Locale.ROOT);
            if (className.contains("docker")) {
                return true;
            }

            // Check for reflective docker plugin methods
            if (hasMethodValue(node, "getContainerId") || hasMethodValue(node, "getDockerTemplate")) {
                return true;
            }

            // Explicit docker plugin descriptions
            String nodeDesc = node.getNodeDescription();
            if (nodeDesc != null) {
                String descLower = nodeDesc.toLowerCase(Locale.ROOT);
                if (descLower.contains("docker container")
                        || descLower.contains("docker dynamic agent")
                        || descLower.contains("docker cloud")
                        || descLower.contains("docker-plugin")
                        || descLower.contains("docker agent")) {
                    return true;
                }
            }

            // Name pattern: strictly starts with docker- or docker_ (avoid matching generic labels or 'container')
            String nodeName = node.getNodeName().toLowerCase(Locale.ROOT);
            if (nodeName.startsWith("docker-") || nodeName.startsWith("docker_")) {
                return true;
            }
        }

        if (computer != null) {
            String compName = computer.getName().toLowerCase(Locale.ROOT);
            if (compName.startsWith("docker-") || compName.startsWith("docker_")) {
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

    private boolean hasMethodValue(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object res = m.invoke(target);
            return res != null && !String.valueOf(res).trim().isEmpty();
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return false;
        }
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
