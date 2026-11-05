package io.jenkins.plugins.fleetlens.api.dto;

import java.io.Serializable;

/**
 * Data Transfer Object containing resolved connection and SSH recipe metadata.
 */
public class ConnectionDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sshUser;
    private String sshHost;
    private int sshPort = 22;
    private String userResolutionSource;
    private String jnlpSecret;
    private String jnlpUrl;
    private String jnlpJarUrl;
    private String primaryCommand;
    private String primaryCommandType; // "INBOUND_CLI", "SSH", "DOCKER_EXEC"

    public ConnectionDto() {}

    public String getSshUser() {
        return sshUser;
    }

    public void setSshUser(String sshUser) {
        this.sshUser = sshUser;
    }

    public String getSshHost() {
        return sshHost;
    }

    public void setSshHost(String sshHost) {
        this.sshHost = sshHost;
    }

    public int getSshPort() {
        return sshPort;
    }

    public void setSshPort(int sshPort) {
        this.sshPort = sshPort;
    }

    public String getUserResolutionSource() {
        return userResolutionSource;
    }

    public void setUserResolutionSource(String userResolutionSource) {
        this.userResolutionSource = userResolutionSource;
    }

    public String getJnlpSecret() {
        return jnlpSecret;
    }

    public void setJnlpSecret(String jnlpSecret) {
        this.jnlpSecret = jnlpSecret;
    }

    public String getJnlpUrl() {
        return jnlpUrl;
    }

    public void setJnlpUrl(String jnlpUrl) {
        this.jnlpUrl = jnlpUrl;
    }

    public String getJnlpJarUrl() {
        return jnlpJarUrl;
    }

    public void setJnlpJarUrl(String jnlpJarUrl) {
        this.jnlpJarUrl = jnlpJarUrl;
    }

    public String getPrimaryCommand() {
        return primaryCommand;
    }

    public void setPrimaryCommand(String primaryCommand) {
        this.primaryCommand = primaryCommand;
    }

    public String getPrimaryCommandType() {
        return primaryCommandType;
    }

    public void setPrimaryCommandType(String primaryCommandType) {
        this.primaryCommandType = primaryCommandType;
    }
}
