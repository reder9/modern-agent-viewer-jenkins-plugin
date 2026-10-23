package io.jenkins.plugins.fleetlens.detector;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.EphemeralNode;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Universal detector for cloud and dynamic ephemeral agents.
 * Identifies hyperscalers and cloud providers (Amazon ECS/Fargate, Oracle Cloud, DigitalOcean,
 * Hetzner, Alibaba Cloud, Linode, OpenShift) and provides deep reflective introspection on any
 * custom or unlisted cloud orchestrators.
 */
@Extension
public class GenericCloudDetector implements AgentProvisionerDetector {

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    public boolean isApplicable(Computer computer, Node node) {
        String cName = computer != null ? computer.getClass().getName().toLowerCase(Locale.ROOT) : "";
        String nName = node != null ? node.getClass().getName().toLowerCase(Locale.ROOT) : "";
        String desc = (node != null && node.getNodeDescription() != null) ? node.getNodeDescription().toLowerCase(Locale.ROOT) : "";
        String all = cName + " " + nName + " " + desc;

        if (all.contains("ecs") || all.contains("amazonecs") || all.contains("fargate")
                || all.contains("oracle") || all.contains("oci")
                || all.contains("digitalocean") || all.contains("droplet")
                || all.contains("hetzner")
                || all.contains("aliyun") || all.contains("alibaba")
                || all.contains("linode")
                || all.contains("openshift")) {
            return true;
        }

        // Standard static/permanent Jenkins DumbSlave nodes must be handled by StaticNodeDetector
        if (node != null && ("DumbSlave".equals(node.getClass().getSimpleName()) || "hudson.slaves.DumbSlave".equals(node.getClass().getName()))) {
            return false;
        }

        if (node instanceof AbstractCloudSlave || node instanceof EphemeralNode) {
            return true;
        }
        if (computer != null && computer.getClass().getName().contains("Cloud")) {
            return true;
        }
        return false;
    }

    @Override
    public ProvisionerDto detect(Computer computer, Node node) {
        String cName = computer != null ? computer.getClass().getName().toLowerCase(Locale.ROOT) : "";
        String nName = node != null ? node.getClass().getName().toLowerCase(Locale.ROOT) : "";
        String desc = (node != null && node.getNodeDescription() != null) ? node.getNodeDescription().toLowerCase(Locale.ROOT) : "";
        String all = cName + " " + nName + " " + desc;

        String id = "cloud";
        String displayName = "Cloud Dynamic Agent";
        String icon = "symbol-cloud";
        String badge = "badge-cloud";

        if (all.contains("ecs") || all.contains("amazonecs") || all.contains("fargate")) {
            id = "aws-ecs";
            displayName = "Amazon ECS / Fargate";
            icon = "symbol-aws";
            badge = "badge-aws";
        } else if (all.contains("oracle") || all.contains("oci")) {
            id = "oci";
            displayName = "Oracle Cloud (OCI)";
            icon = "symbol-cloud";
            badge = "badge-oci";
        } else if (all.contains("digitalocean") || all.contains("droplet")) {
            id = "digitalocean";
            displayName = "DigitalOcean";
            icon = "symbol-cloud";
            badge = "badge-digitalocean";
        } else if (all.contains("hetzner")) {
            id = "hetzner";
            displayName = "Hetzner Cloud";
            icon = "symbol-cloud";
            badge = "badge-hetzner";
        } else if (all.contains("aliyun") || all.contains("alibaba")) {
            id = "aliyun";
            displayName = "Alibaba Cloud";
            icon = "symbol-cloud";
            badge = "badge-aliyun";
        } else if (all.contains("linode")) {
            id = "linode";
            displayName = "Linode Cloud";
            icon = "symbol-cloud";
            badge = "badge-cloud";
        } else if (all.contains("openshift")) {
            id = "openshift";
            displayName = "Red Hat OpenShift";
            icon = "symbol-kubernetes";
            badge = "badge-k8s";
        } else if (node != null) {
            String simple = node.getClass().getSimpleName();
            simple = simple.replaceAll("(?i)(Slave|Computer|Node|Agent)", "").trim();
            if (!simple.isEmpty() && !simple.equalsIgnoreCase("Dumb")) {
                displayName = simple + " Cloud";
            }
        }

        ProvisionerDto dto = new ProvisionerDto(id, displayName, icon, badge);
        dto.addDetail("Type", "Dynamic Ephemeral Cloud Agent");

        if (node != null) {
            dto.addDetail("Node Class", node.getClass().getSimpleName());
            tryReflectiveMethods(node, dto);
        }
        if (computer != null) {
            tryReflectiveMethods(computer, dto);
        }

        return dto;
    }

    private void tryReflectiveMethods(Object target, ProvisionerDto dto) {
        tryReflective(target, "getCloudName", "Cloud Cluster", dto);
        tryReflective(target, "getTemplateName", "Template", dto);
        tryReflective(target, "getTemplate", "Template", dto);
        tryReflective(target, "getInstanceId", "Instance ID", dto);
        tryReflective(target, "getServerId", "Server ID", dto);
        tryReflective(target, "getDropletId", "Droplet ID", dto);
        tryReflective(target, "getInstanceType", "Machine Type", dto);
        tryReflective(target, "getServerType", "Machine Type", dto);
        tryReflective(target, "getSizeId", "Plan / Size", dto);
        tryReflective(target, "getRegion", "Region", dto);
        tryReflective(target, "getRegionId", "Region", dto);
        tryReflective(target, "getZone", "Availability Zone", dto);
        tryReflective(target, "getImageId", "OS Image / AMI", dto);
        tryReflective(target, "getClusterArn", "Cluster ARN", dto);
        tryReflective(target, "getTaskArn", "Task ARN", dto);
    }

    private void tryReflective(Object target, String methodName, String detailKey, ProvisionerDto dto) {
        if (dto.getDetails().containsKey(detailKey)) return;
        try {
            Method m = target.getClass().getMethod(methodName);
            Object res = m.invoke(target);
            if (res != null) {
                String val = String.valueOf(res).trim();
                if (!val.isEmpty()) {
                    dto.addDetail(detailKey, val);
                }
            }
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException ignored) {
            // Method not available on target object
        }
    }
}
