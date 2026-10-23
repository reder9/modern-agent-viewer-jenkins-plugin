package io.jenkins.plugins.fleetlens.detector;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;
import java.lang.reflect.Method;
import java.util.Locale;

@Extension
public class GcpDetector implements AgentProvisionerDetector {

    @Override
    public int getPriority() {
        return 75;
    }

    @Override
    public boolean isApplicable(Computer computer, Node node) {
        if (computer != null && (computer.getClass().getName().contains("GCE") || computer.getClass().getName().toLowerCase(Locale.ROOT).contains("gcp"))) {
            return true;
        }
        if (node != null) {
            String className = node.getClass().getName();
            if (className.contains("GCE") || className.toLowerCase(Locale.ROOT).contains("gcp")) {
                return true;
            }
            String nodeDesc = node.getNodeDescription();
            if (nodeDesc != null && (nodeDesc.contains("GCE") || nodeDesc.toLowerCase(Locale.ROOT).contains("google cloud"))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ProvisionerDto detect(Computer computer, Node node) {
        ProvisionerDto dto = new ProvisionerDto("gcp", "Google Cloud Engine", "symbol-gcp", "badge-gcp");
        dto.addDetail("Type", "Google Cloud Engine VM");

        if (node != null) {
            tryReflectiveMethod(node, "getInstanceName", "Instance Name", dto);
            tryReflectiveMethod(node, "getZone", "Zone", dto);
            tryReflectiveMethod(node, "getProjectId", "Project", dto);
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
