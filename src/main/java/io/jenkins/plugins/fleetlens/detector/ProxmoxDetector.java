package io.jenkins.plugins.fleetlens.detector;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;
import java.lang.reflect.Method;
import java.util.Locale;

@Extension
public class ProxmoxDetector implements AgentProvisionerDetector {

    @Override
    public int getPriority() {
        return 80;
    }

    @Override
    public boolean isApplicable(Computer computer, Node node) {
        if (computer != null) {
            String compClass = computer.getClass().getName().toLowerCase(Locale.ROOT);
            if (compClass.contains("proxmox")) {
                return true;
            }
            String compName = computer.getName().toLowerCase(Locale.ROOT);
            if (compName.startsWith("pve-") || compName.contains("proxmox")) {
                return true;
            }
        }
        if (node != null) {
            String className = node.getClass().getName().toLowerCase(Locale.ROOT);
            if (className.contains("proxmox") || className.contains("virtualmachineslave")) {
                return true;
            }
            String nodeDesc = node.getNodeDescription();
            if (nodeDesc != null) {
                String descLower = nodeDesc.toLowerCase(Locale.ROOT);
                if (descLower.contains("proxmox") || descLower.contains("pve")) {
                    return true;
                }
            }
            String nodeName = node.getNodeName().toLowerCase(Locale.ROOT);
            if (nodeName.startsWith("pve-") || nodeName.contains("proxmox")) {
                return true;
            }
            for (Label l : node.getAssignedLabels()) {
                String lname = l.getName().toLowerCase(Locale.ROOT);
                if (lname.equals("proxmox") || lname.equals("pve") || lname.contains("proxmox-") || lname.contains("pve-")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public ProvisionerDto detect(Computer computer, Node node) {
        ProvisionerDto dto = new ProvisionerDto("proxmox", "Proxmox VE", "symbol-proxmox", "badge-proxmox");
        dto.addDetail("Type", "Proxmox Virtual Environment");

        if (node != null) {
            tryReflectiveMethod(node, "getVmid", "VM ID", dto);
            tryReflectiveMethod(node, "getVmId", "VM ID", dto);
            tryReflectiveMethod(node, "getTemplateName", "Template", dto);
            tryReflectiveMethod(node, "getTemplate", "Template", dto);
            tryReflectiveMethod(node, "getCloudName", "Cloud Name", dto);
        }
        if (computer != null) {
            tryReflectiveMethod(computer, "getVmid", "VM ID", dto);
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
