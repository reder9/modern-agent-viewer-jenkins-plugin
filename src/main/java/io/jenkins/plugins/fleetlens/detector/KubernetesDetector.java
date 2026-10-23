package io.jenkins.plugins.fleetlens.detector;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;
import java.lang.reflect.Method;
import java.util.Locale;

@Extension
public class KubernetesDetector implements AgentProvisionerDetector {

    @Override
    public int getPriority() {
        return 90;
    }

    @Override
    public boolean isApplicable(Computer computer, Node node) {
        if (computer != null && computer.getClass().getName().toLowerCase(Locale.ROOT).contains("kubernetes")) {
            return true;
        }
        if (node != null) {
            String className = node.getClass().getName().toLowerCase(Locale.ROOT);
            if (className.contains("kubernetes")) {
                return true;
            }
            String nodeDesc = node.getNodeDescription();
            if (nodeDesc != null && nodeDesc.toLowerCase(Locale.ROOT).contains("kubernetes")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ProvisionerDto detect(Computer computer, Node node) {
        ProvisionerDto dto = new ProvisionerDto("kubernetes", "Kubernetes Pod", "symbol-kubernetes", "badge-k8s");
        dto.addDetail("Type", "Kubernetes Pod Agent");

        if (node != null) {
            tryReflectiveMethod(node, "getPodName", "Pod Name", dto);
            tryReflectiveMethod(node, "getNamespace", "Namespace", dto);
            tryReflectiveMethod(node, "getCloudName", "Cluster / Cloud", dto);
            tryReflectiveMethod(node, "getTemplate", "Pod Template", dto);
        }
        if (computer != null) {
            tryReflectiveMethod(computer, "getPodName", "Pod Name", dto);
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
