package io.jenkins.plugins.fleetlens.detector;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Detects agents provisioned and managed by VMware vSphere (vsphere-cloud-plugin).
 * Prioritized higher than generic Docker detector (90 vs 70) so that vSphere Linux/Windows
 * VMs with Docker installed or docker in their names are correctly attributed to VMware vSphere.
 */
@Extension
public class VSphereDetector implements AgentProvisionerDetector {

    @Override
    public int getPriority() {
        return 90;
    }

    @Override
    public boolean isApplicable(Computer computer, Node node) {
        if (computer != null) {
            String compClass = computer.getClass().getName().toLowerCase(Locale.ROOT);
            if (compClass.contains("vsphere") || compClass.contains("vmware")) {
                return true;
            }
            String compName = computer.getName().toLowerCase(Locale.ROOT);
            if (compName.contains("vsphere") || compName.contains("vmware") || compName.contains("esxi")) {
                return true;
            }
            if (hasMethodValue(computer, "getVmName") || hasMethodValue(computer, "getVsphereCloudName")) {
                return true;
            }
            try {
                EnvVars env = computer.getEnvironment();
                if (env != null && (env.containsKey("VSPHERE_IP")
                        || env.containsKey("VSPHERE_VM_NAME")
                        || env.containsKey("VSPHERE_SNAPSHOT"))) {
                    return true;
                }
            } catch (IOException | InterruptedException ignored) {
            }
        }

        if (node != null) {
            String className = node.getClass().getName().toLowerCase(Locale.ROOT);
            if (className.contains("vsphere") || className.contains("vmware")) {
                return true;
            }

            // Reflective checks on vSphereCloudSlave / vSphereCloudProvisionedSlave
            if (hasMethodValue(node, "getVmName")
                    || hasMethodValue(node, "getVsphereCloudName")
                    || hasMethodValue(node, "getTemplateDescription")) {
                return true;
            }

            String nodeDesc = node.getNodeDescription();
            if (nodeDesc != null) {
                String descLower = nodeDesc.toLowerCase(Locale.ROOT);
                if (descLower.contains("vsphere")
                        || descLower.contains("vmware")
                        || descLower.contains("esxi")
                        || descLower.contains("vcenter")) {
                    return true;
                }
            }

            String nodeName = node.getNodeName().toLowerCase(Locale.ROOT);
            if (nodeName.contains("vsphere") || nodeName.contains("vmware") || nodeName.contains("esxi")) {
                return true;
            }

            for (Label l : node.getAssignedLabels()) {
                String lname = l.getName().toLowerCase(Locale.ROOT);
                if (lname.equals("vsphere") || lname.equals("vmware") || lname.equals("esxi")
                        || lname.contains("vsphere-") || lname.contains("-vsphere")
                        || lname.contains("vmware-") || lname.contains("-vmware")) {
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
            tryReflectiveMethod(node, "getTemplateDescription", "Template", dto);
            tryReflectiveMethod(node, "getVsphereCloudName", "vCenter Cloud", dto);
            tryReflectiveMethod(node, "getCloudName", "vCenter Cloud", dto);
            tryReflectiveMethod(node, "getResourcePool", "Resource Pool", dto);
            tryReflectiveMethod(node, "getCluster", "Cluster", dto);
            tryReflectiveMethod(node, "getDatastore", "Datastore", dto);
            tryReflectiveMethod(node, "getIpAddress", "IP Address", dto);
        }
        if (computer != null) {
            tryReflectiveMethod(computer, "getVmName", "VM Name", dto);
            tryReflectiveMethod(computer, "getCloudName", "vCenter Cloud", dto);
            tryReflectiveMethod(computer, "getIpAddress", "IP Address", dto);
            try {
                EnvVars env = computer.getEnvironment();
                if (env != null) {
                    if (env.containsKey("VSPHERE_VM_NAME") && !dto.getDetails().containsKey("VM Name")) {
                        dto.addDetail("VM Name", env.get("VSPHERE_VM_NAME"));
                    }
                    if (env.containsKey("VSPHERE_IP") && !dto.getDetails().containsKey("IP Address")) {
                        dto.addDetail("IP Address", env.get("VSPHERE_IP"));
                    }
                    if (env.containsKey("VSPHERE_SNAPSHOT") && !dto.getDetails().containsKey("Snapshot")) {
                        dto.addDetail("Snapshot", env.get("VSPHERE_SNAPSHOT"));
                    }
                }
            } catch (IOException | InterruptedException ignored) {
            }
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
