package io.jenkins.plugins.fleetlens.detector;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;
import java.lang.reflect.Method;

@Extension
public class AwsEc2Detector implements AgentProvisionerDetector {

    @Override
    public int getPriority() {
        return 85;
    }

    @Override
    public boolean isApplicable(Computer computer, Node node) {
        if (computer != null && (computer.getClass().getName().contains("EC2") || computer.getClass().getName().contains("Ec2"))) {
            return true;
        }
        if (node != null) {
            String className = node.getClass().getName();
            if (className.contains("EC2") || className.contains("Ec2") || className.contains("aws")) {
                return true;
            }
            String nodeDesc = node.getNodeDescription();
            if (nodeDesc != null && (nodeDesc.contains("EC2") || nodeDesc.contains("Amazon EC2"))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ProvisionerDto detect(Computer computer, Node node) {
        ProvisionerDto dto = new ProvisionerDto("aws-ec2", "AWS EC2", "symbol-aws", "badge-aws");
        dto.addDetail("Type", "Amazon Web Services EC2");

        if (node != null) {
            tryReflectiveMethod(node, "getInstanceId", "Instance ID", dto);
            tryReflectiveMethod(node, "getEc2Type", "Instance Type", dto);
            tryReflectiveMethod(node, "getRemoteAdmin", "Remote User", dto);
            tryReflectiveMethod(node, "getCloudName", "Cloud Name", dto);
        }
        if (computer != null) {
            tryReflectiveMethod(computer, "getInstanceId", "Instance ID", dto);
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
