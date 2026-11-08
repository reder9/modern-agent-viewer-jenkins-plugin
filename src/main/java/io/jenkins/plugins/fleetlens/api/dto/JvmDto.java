package io.jenkins.plugins.fleetlens.api.dto;

import java.io.Serializable;

/**
 * Data Transfer Object representing agent JVM runtime version and controller compatibility.
 */
public class JvmDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String version;            // e.g. "21.0.5" or "17.0.10"
    private int majorVersion;          // e.g. 21 or 17
    private int controllerMajorVersion;// e.g. 21
    private boolean versionMismatch;   // true if majorVersion != controllerMajorVersion
    private String mismatchWarning;    // human explanation if mismatched

    public JvmDto() {}

    public JvmDto(String version, int majorVersion, int controllerMajorVersion, boolean versionMismatch, String mismatchWarning) {
        this.version = version;
        this.majorVersion = majorVersion;
        this.controllerMajorVersion = controllerMajorVersion;
        this.versionMismatch = versionMismatch;
        this.mismatchWarning = mismatchWarning;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public void setMajorVersion(int majorVersion) {
        this.majorVersion = majorVersion;
    }

    public int getControllerMajorVersion() {
        return controllerMajorVersion;
    }

    public void setControllerMajorVersion(int controllerMajorVersion) {
        this.controllerMajorVersion = controllerMajorVersion;
    }

    public boolean isVersionMismatch() {
        return versionMismatch;
    }

    public void setVersionMismatch(boolean versionMismatch) {
        this.versionMismatch = versionMismatch;
    }

    public String getMismatchWarning() {
        return mismatchWarning;
    }

    public void setMismatchWarning(String mismatchWarning) {
        this.mismatchWarning = mismatchWarning;
    }
}
