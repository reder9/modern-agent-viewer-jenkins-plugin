package io.jenkins.plugins.fleetlens.api.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data Transfer Object representing the provisioner or origin that created the node.
 */
public class ProvisionerDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String displayName;
    private String icon;
    private String badgeClass;
    private Map<String, String> details = new LinkedHashMap<>();

    public ProvisionerDto() {}

    public ProvisionerDto(String id, String displayName, String icon, String badgeClass) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.badgeClass = badgeClass;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    public void setBadgeClass(String badgeClass) {
        this.badgeClass = badgeClass;
    }

    public Map<String, String> getDetails() {
        return Collections.unmodifiableMap(details);
    }

    public void addDetail(String key, String value) {
        if (key != null && value != null) {
            this.details.put(key, value);
        }
    }

    public void setDetails(Map<String, String> details) {
        this.details = details != null ? new LinkedHashMap<>(details) : new LinkedHashMap<>();
    }
}
