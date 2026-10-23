package io.jenkins.plugins.fleetlens.detector;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;
import java.lang.reflect.Method;
import java.util.Locale;

@Extension
public class NomadDetector implements AgentProvisionerDetector {

    @Override
    public int getPriority() {
        return 80;
    }

    @Override
    public boolean isApplicable(Computer computer, Node node) {
        if (computer != null) {
            String compClass = computer.getClass().getName().toLowerCase(Locale.ROOT);
            if (compClass.contains("nomad")) {
                return true;
            }
        }
        if (node != null) {
            String className = node.getClass().getName().toLowerCase(Locale.ROOT);
            if (className.contains("nomad")) {
                return true;
            }
            String nodeDesc = node.getNodeDescription();
            if (nodeDesc != null) {
                String descLower = nodeDesc.toLowerCase(Locale.ROOT);
                if (descLower.contains("nomad") || descLower.contains("hashicorp nomad")) {
                    return true;
                }
            }
            String nodeName = node.getNodeName().toLowerCase(Locale.ROOT);
            if (nodeName.startsWith("nomad-")) {
                return true;
            }
            for (Label l : node.getAssignedLabels()) {
                String lname = l.getName().toLowerCase(Locale.ROOT);
                if (lname.equals("nomad") || lname.contains("nomad-")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public ProvisionerDto detect(Computer computer, Node node) {
        ProvisionerDto dto = new ProvisionerDto("nomad", "HashiCorp Nomad", "symbol-nomad", "badge-nomad");
        dto.addDetail("Type", "HashiCorp Nomad Task Runner");

        if (node != null) {
            tryReflectiveMethod(node, "getJobName", "Job Name", dto);
            tryReflectiveMethod(node, "getNomadJob", "Job Name", dto);
            tryReflectiveMethod(node, "getDatacenter", "Datacenter", dto);
            tryReflectiveMethod(node, "getNomadCloud", "Nomad Cloud", dto);
            tryReflectiveMethod(node, "getSlaveTemplate", "Template", dto);
        }
        if (computer != null) {
            tryReflectiveMethod(computer, "getNomadCloud", "Nomad Cloud", dto);
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
