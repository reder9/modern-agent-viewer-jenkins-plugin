package io.jenkins.plugins.fleetlens.detector;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;
import java.lang.reflect.Method;
import java.util.Locale;

@Extension
public class VSphereDetector implements AgentProvisionerDetector {

    @Override
    public int getPriority() {
        return 80;
    }

    @Override
    public boolean isApplicable(Computer computer, Node node) {
        if (computer != null) {
            String compClass = computer.getClass().getName().toLowerCase(Locale.ROOT);
            if (compClass.contains("vsphere") || compClass.contains("vmware")) {
                return true;
            }
        }
        if (node != null) {
            String className = node.getClass().getName().toLowerCase(Locale.ROOT);
            if (className.contains("vsphere") || className.contains("vmware")) {
                return true;
            }
            String nodeDesc = node.getNodeDescription();
            if (nodeDesc != null) {
                String descLower = nodeDesc.toLowerCase(Locale.ROOT);
                if (descLower.contains("vsphere") || descLower.contains("vmware") || descLower.contains("esxi")) {
                    return true;
                }
            }
            String nodeName = node.getNodeName().toLowerCase(Locale.ROOT);
            if (nodeName.startsWith("vsphere-") || nodeName.startsWith("vmware-")) {
                return true;
            }
            for (Label l : node.getAssignedLabels()) {
                String lname = l.getName().toLowerCase(Locale.ROOT);
                if (lname.equals("vsphere") || lname.equals("vmware") || lname.equals("esxi")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public ProvisionerDto detect(Computer computer, Node node) {
        ProvisionerDto dto = new ProvisionerDto("vsphere", "VMware vSphere", "symbol-vsphere", "badge-vsphere");
        dto.addDetail("Type", "VMware vSphere / ESXi VM");

        if (node != null) {
            tryReflectiveMethod(node, "getVmName", "VM Name", dto);
            tryReflectiveMethod(node, "getTemplateName", "Template", dto);
            tryReflectiveMethod(node, "getTemplate", "Template", dto);
            tryReflectiveMethod(node, "getVsphereCloudName", "vCenter Cloud", dto);
            tryReflectiveMethod(node, "getCloudName", "vCenter Cloud", dto);
            tryReflectiveMethod(node, "getResourcePool", "Resource Pool", dto);
            tryReflectiveMethod(node, "getCluster", "Cluster", dto);
        }
        if (computer != null) {
            tryReflectiveMethod(computer, "getVmName", "VM Name", dto);
            tryReflectiveMethod(computer, "getCloudName", "vCenter Cloud", dto);
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
