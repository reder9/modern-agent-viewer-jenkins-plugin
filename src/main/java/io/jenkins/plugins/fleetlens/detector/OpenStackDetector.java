package io.jenkins.plugins.fleetlens.detector;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;
import java.lang.reflect.Method;
import java.util.Locale;

@Extension
public class OpenStackDetector implements AgentProvisionerDetector {

    @Override
    public int getPriority() {
        return 80;
    }

    @Override
    public boolean isApplicable(Computer computer, Node node) {
        if (computer != null) {
            String compClass = computer.getClass().getName().toLowerCase(Locale.ROOT);
            if (compClass.contains("openstack") || compClass.contains("jclouds")) {
                return true;
            }
        }
        if (node != null) {
            String className = node.getClass().getName().toLowerCase(Locale.ROOT);
            if (className.contains("openstack") || className.contains("jclouds")) {
                return true;
            }
            String nodeDesc = node.getNodeDescription();
            if (nodeDesc != null) {
                String descLower = nodeDesc.toLowerCase(Locale.ROOT);
                if (descLower.contains("openstack") || descLower.contains("nova")) {
                    return true;
                }
            }
            String nodeName = node.getNodeName().toLowerCase(Locale.ROOT);
            if (nodeName.startsWith("openstack-") || nodeName.startsWith("nova-")) {
                return true;
            }
            for (Label l : node.getAssignedLabels()) {
                String lname = l.getName().toLowerCase(Locale.ROOT);
                if (lname.equals("openstack") || lname.contains("openstack-")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public ProvisionerDto detect(Computer computer, Node node) {
        ProvisionerDto dto = new ProvisionerDto("openstack", "OpenStack", "symbol-openstack", "badge-openstack");
        dto.addDetail("Type", "OpenStack Dynamic Cloud Agent");

        if (node != null) {
            tryReflectiveMethod(node, "getHardwareId", "Flavor", dto);
            tryReflectiveMethod(node, "getFlavor", "Flavor", dto);
            tryReflectiveMethod(node, "getImageId", "Image ID", dto);
            tryReflectiveMethod(node, "getZone", "Availability Zone", dto);
            tryReflectiveMethod(node, "getAvailabilityZone", "Availability Zone", dto);
            tryReflectiveMethod(node, "getCloudName", "Cloud Name", dto);
        }
        if (computer != null) {
            tryReflectiveMethod(computer, "getCloudName", "Cloud Name", dto);
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
