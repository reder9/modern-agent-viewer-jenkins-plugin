package io.jenkins.plugins.fleetlens.detector;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;
import java.lang.reflect.Method;
import java.util.Locale;

@Extension
public class AzureDetector implements AgentProvisionerDetector {

    @Override
    public int getPriority() {
        return 80;
    }

    @Override
    public boolean isApplicable(Computer computer, Node node) {
        if (computer != null && computer.getClass().getName().toLowerCase(Locale.ROOT).contains("azure")) {
            return true;
        }
        if (node != null) {
            String className = node.getClass().getName().toLowerCase(Locale.ROOT);
            if (className.contains("azure")) {
                return true;
            }
            String nodeDesc = node.getNodeDescription();
            if (nodeDesc != null && nodeDesc.toLowerCase(Locale.ROOT).contains("azure")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ProvisionerDto detect(Computer computer, Node node) {
        ProvisionerDto dto = new ProvisionerDto("azure", "Azure VM", "symbol-azure", "badge-azure");
        dto.addDetail("Type", "Microsoft Azure VM");

        if (node != null) {
            tryReflectiveMethod(node, "getVmSize", "VM Size", dto);
            tryReflectiveMethod(node, "getResourceGroupName", "Resource Group", dto);
            tryReflectiveMethod(node, "getCloudName", "Cloud Name", dto);
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
