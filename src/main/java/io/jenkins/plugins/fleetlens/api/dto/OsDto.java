package io.jenkins.plugins.fleetlens.api.dto;

import java.io.Serializable;

/**
 * Data Transfer Object representing operating system and CPU architecture details.
 */
public class OsDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String family;      // LINUX, WINDOWS, MACOS, BSD, OTHER
    private String name;        // e.g. Linux, Windows 11
    private String prettyName;  // e.g. Ubuntu 24.04.1 LTS, Windows Server 2022
    private String version;
    private String arch;        // e.g. x86_64, amd64, arm64, aarch64
    private String icon;        // e.g. symbol-linux, symbol-windows, symbol-apple

    public OsDto() {}

    public OsDto(String family, String name, String prettyName, String version, String arch, String icon) {
        this.family = family;
        this.name = name;
        this.prettyName = prettyName;
        this.version = version;
        this.arch = arch;
        this.icon = icon;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrettyName() {
        return prettyName;
    }

    public void setPrettyName(String prettyName) {
        this.prettyName = prettyName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getArch() {
        return arch;
    }

    public void setArch(String arch) {
        this.arch = arch;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }
}
