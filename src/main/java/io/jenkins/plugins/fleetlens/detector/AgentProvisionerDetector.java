package io.jenkins.plugins.fleetlens.detector;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Computer;
import hudson.model.Node;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extension point for detecting what provisioned or manages an agent (Docker, Kubernetes, AWS, Static, etc.).
 */
public interface AgentProvisionerDetector extends ExtensionPoint {

    /**
     * Determines the execution order. Higher numbers run before lower numbers.
     */
    default int getPriority() {
        return 50;
    }

    /**
     * Returns true if this detector matches the given computer/node.
     */
    boolean isApplicable(Computer computer, Node node);

    /**
     * Detects provisioner details and returns a populated ProvisionerDto.
     */
    ProvisionerDto detect(Computer computer, Node node);

    /**
     * Convenience method to find all registered detectors ordered by priority descending.
     */
    static List<AgentProvisionerDetector> all() {
        return ExtensionList.lookup(AgentProvisionerDetector.class)
                .stream()
                .sorted(Comparator.comparingInt(AgentProvisionerDetector::getPriority).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Resolves the provisioner for a given computer and node by consulting registered detectors.
     */
    static ProvisionerDto resolve(Computer computer, Node node) {
        for (AgentProvisionerDetector detector : all()) {
            try {
                if (detector.isApplicable(computer, node)) {
                    ProvisionerDto dto = detector.detect(computer, node);
                    if (dto != null) {
                        return dto;
                    }
                }
            } catch (Exception ignored) {
                // Ignore detector errors and try next detector
            }
        }
        return new ProvisionerDto("static", "Static Node", "symbol-server", "badge-static");
    }
}
