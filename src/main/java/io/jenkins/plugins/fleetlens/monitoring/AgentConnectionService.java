package io.jenkins.plugins.fleetlens.monitoring;

import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.slaves.ComputerLauncher;
import io.jenkins.plugins.fleetlens.api.dto.ConnectionDto;
import io.jenkins.plugins.fleetlens.api.dto.OsDto;
import jenkins.model.Jenkins;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves accurate connection metadata, SSH identity credentials, and one-click
 * launch commands for Jenkins agents.
 */
public final class AgentConnectionService {

    private static final Logger LOGGER = Logger.getLogger(AgentConnectionService.class.getName());

    // Caches runtime user identities discovered while agents are online
    private static final Map<String, String> RUNTIME_USER_CACHE = new ConcurrentHashMap<>();

    private static final Pattern MAC_HOME_PATTERN = Pattern.compile("^/Users/([a-zA-Z0-9._-]+)(?:/.*)?$");
    private static final Pattern LINUX_HOME_PATTERN = Pattern.compile("^/home/([a-zA-Z0-9._-]+)(?:/.*)?$");
    private static final Pattern WIN_HOME_PATTERN = Pattern.compile("^[a-zA-Z]:[/\\\\]Users[/\\\\]([a-zA-Z0-9._-]+)(?:[/\\\\].*)?$", Pattern.CASE_INSENSITIVE);

    private AgentConnectionService() {}

    /**
     * Resolves comprehensive connection details for a computer and node.
     */
    public static ConnectionDto resolveConnection(Computer computer, Node node, String ip, OsDto os) {
        ConnectionDto dto = new ConnectionDto();
        if (computer == null) {
            return dto;
        }

        String nodeName = computer.getName();
        ComputerLauncher launcher = getLauncher(computer, node);

        String sshUser = null;
        String sshHost = ip;
        int sshPort = 22;
        String userSource = null;

        // 1. Tier 1: Authoritative SSHLauncher Credentials (if configured via SSH)
        if (launcher != null && launcher.getClass().getName().contains("SSHLauncher")) {
            try {
                // Host from launcher
                Method getHostMethod = findMethod(launcher.getClass(), "getHost");
                if (getHostMethod != null) {
                    Object h = getHostMethod.invoke(launcher);
                    if (h instanceof String && !((String) h).trim().isEmpty()) {
                        sshHost = ((String) h).trim();
                    }
                }

                // Port from launcher
                Method getPortMethod = findMethod(launcher.getClass(), "getPort");
                if (getPortMethod != null) {
                    Object p = getPortMethod.invoke(launcher);
                    if (p instanceof Integer && (Integer) p > 0) {
                        sshPort = (Integer) p;
                    }
                }

                // Username directly from launcher credentials
                Method getCredsMethod = findMethod(launcher.getClass(), "getCredentials");
                if (getCredsMethod != null) {
                    Object creds = getCredsMethod.invoke(launcher);
                    if (creds != null) {
                        Method getUsernameMethod = findMethod(creds.getClass(), "getUsername");
                        if (getUsernameMethod != null) {
                            Object u = getUsernameMethod.invoke(creds);
                            if (u instanceof String && !((String) u).trim().isEmpty()) {
                                sshUser = ((String) u).trim();
                                userSource = "SSH Credentials (" + creds.getClass().getSimpleName() + ")";
                            }
                        }
                    }
                }

                // Fallback to credentialsId lookup if direct credentials returned null
                if (sshUser == null) {
                    Method getCredIdMethod = findMethod(launcher.getClass(), "getCredentialsId");
                    if (getCredIdMethod != null) {
                        Object cid = getCredIdMethod.invoke(launcher);
                        if (cid instanceof String && !((String) cid).trim().isEmpty()) {
                            String found = lookupUsernameFromCredentialsId((String) cid);
                            if (found != null) {
                                sshUser = found;
                                userSource = "SSH Credentials ID";
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Error extracting SSHLauncher details for " + nodeName, e);
            }
        }

        // 2. Tier 2: Agent Runtime Remoting Telemetry (user.name from systemProperties)
        if (sshUser == null) {
            try {
                Map<Object, Object> sysProps = computer.getSystemProperties();
                if (sysProps != null && sysProps.containsKey("user.name")) {
                    Object val = sysProps.get("user.name");
                    if (val instanceof String && !((String) val).trim().isEmpty()) {
                        sshUser = ((String) val).trim();
                        userSource = "Agent Runtime Process (user.name)";
                        RUNTIME_USER_CACHE.put(nodeName, sshUser);
                    }
                }
            } catch (Exception ignored) {}
        }

        // Check cached runtime user if currently offline
        if (sshUser == null && RUNTIME_USER_CACHE.containsKey(nodeName)) {
            sshUser = RUNTIME_USER_CACHE.get(nodeName);
            userSource = "Cached Agent Process User";
        }

        // 3. Tier 3: Inferred from Workspace Path (remoteFS)
        if (sshUser == null && node != null) {
            String remoteFs = getRemoteFs(node);
            if (remoteFs != null && !remoteFs.trim().isEmpty()) {
                String inferred = inferUserFromPath(remoteFs);
                if (inferred != null) {
                    sshUser = inferred;
                    userSource = "Workspace Path (" + remoteFs + ")";
                }
            }
        }

        // 4. Tier 4: Cloud Instance Remote Admin (e.g. AWS EC2, Azure)
        if (sshUser == null && node != null) {
            String remoteAdmin = getCloudRemoteAdmin(node);
            if (remoteAdmin != null) {
                sshUser = remoteAdmin;
                userSource = "Cloud Provider Remote Admin";
            }
        }

        // 5. Tier 5: Intelligent OS Default Fallbacks
        if (sshUser == null) {
            String osFam = (os != null && os.getFamily() != null) ? os.getFamily() : "OTHER";
            if ("MACOS".equalsIgnoreCase(osFam)) {
                sshUser = "admin";
                userSource = "macOS Default";
            } else if ("WINDOWS".equalsIgnoreCase(osFam)) {
                sshUser = "Administrator";
                userSource = "Windows Default";
            } else {
                sshUser = "jenkins";
                userSource = "Linux Default";
            }
        }

        dto.setSshUser(sshUser);
        dto.setSshHost(sshHost);
        dto.setSshPort(sshPort);
        dto.setUserResolutionSource(userSource);

        // Resolve JNLP Inbound HMAC Token & URLs
        String rootUrl = "http://localhost:8080/";
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null && jenkins.getRootUrl() != null && !jenkins.getRootUrl().isEmpty()) {
            rootUrl = jenkins.getRootUrl();
        }
        if (!rootUrl.endsWith("/")) {
            rootUrl += "/";
        }

        String jnlpMac = null;
        try {
            if (computer instanceof hudson.slaves.SlaveComputer) {
                jnlpMac = ((hudson.slaves.SlaveComputer) computer).getJnlpMac();
            }
        } catch (Exception ignored) {}

        dto.setJnlpSecret(jnlpMac);
        dto.setJnlpJarUrl(rootUrl + "jnlpJars/agent.jar");
        dto.setJnlpUrl(rootUrl + computer.getUrl() + "slave-agent.jnlp");

        // Determine Primary One-Click Command
        boolean isLaunchSupported = computer.isLaunchSupported();
        boolean isWindows = (os != null && "WINDOWS".equalsIgnoreCase(os.getFamily()));
        String remoteFs = getRemoteFs(node);
        if (remoteFs == null) remoteFs = "";

        if (!isLaunchSupported && jnlpMac != null && !"(built-in)".equalsIgnoreCase(nodeName)) {
            // Inbound Agent (JNLP) -> Primary command is the official CLI command with verified secret
            dto.setPrimaryCommandType("INBOUND_CLI");
            if (isWindows) {
                dto.setPrimaryCommand("curl.exe -sO " + rootUrl + "jnlpJars/agent.jar; java -jar agent.jar -url " + rootUrl + " -secret " + jnlpMac + " -name \"" + nodeName + "\"" + (!remoteFs.isEmpty() ? " -workDir \"" + remoteFs + "\"" : ""));
            } else {
                dto.setPrimaryCommand("curl -sO " + rootUrl + "jnlpJars/agent.jar && java -jar agent.jar -url " + rootUrl + " -secret " + jnlpMac + " -name \"" + nodeName + "\"" + (!remoteFs.isEmpty() ? " -workDir \"" + remoteFs + "\"" : ""));
            }
        } else {
            // SSH / Cloud Agent -> Primary command is SSH
            dto.setPrimaryCommandType("SSH");
            String targetHost = (sshHost != null && !sshHost.isEmpty()) ? sshHost : (ip != null ? ip : nodeName);
            String portParam = (sshPort != 22 && sshPort > 0) ? "-p " + sshPort + " " : "";
            dto.setPrimaryCommand("ssh " + portParam + sshUser + "@" + targetHost);
        }

        return dto;
    }

    /**
     * Extracts username from typical filesystem workspace patterns.
     */
    public static String inferUserFromPath(String path) {
        if (path == null) return null;
        String trimmed = path.trim();

        Matcher mMac = MAC_HOME_PATTERN.matcher(trimmed);
        if (mMac.matches()) {
            return mMac.group(1);
        }

        Matcher mLinux = LINUX_HOME_PATTERN.matcher(trimmed);
        if (mLinux.matches()) {
            return mLinux.group(1);
        }

        Matcher mWin = WIN_HOME_PATTERN.matcher(trimmed);
        if (mWin.matches()) {
            return mWin.group(1);
        }

        return null;
    }

    private static ComputerLauncher getLauncher(Computer computer, Node node) {
        if (node instanceof Slave) {
            return ((Slave) node).getLauncher();
        }
        if (computer instanceof hudson.slaves.SlaveComputer) {
            return ((hudson.slaves.SlaveComputer) computer).getLauncher();
        }
        return null;
    }

    private static String getRemoteFs(Node node) {
        if (node instanceof Slave) {
            return ((Slave) node).getRemoteFS();
        }
        if (node != null && node.getRootPath() != null) {
            return node.getRootPath().getRemote();
        }
        return null;
    }

    private static String getCloudRemoteAdmin(Node node) {
        try {
            Class<?> ec2SlaveClass = Class.forName("hudson.plugins.ec2.EC2Slave");
            if (ec2SlaveClass.isInstance(node)) {
                Method getRemoteAdmin = findMethod(node.getClass(), "getRemoteAdmin");
                if (getRemoteAdmin != null) {
                    Object admin = getRemoteAdmin.invoke(node);
                    if (admin instanceof String && !((String) admin).trim().isEmpty()) {
                        return ((String) admin).trim();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String lookupUsernameFromCredentialsId(String credentialsId) {
        if (credentialsId == null || credentialsId.trim().isEmpty()) {
            return null;
        }
        try {
            Class<?> cpClass = Class.forName("com.cloudbees.plugins.credentials.CredentialsProvider");
            Class<?> credClass = Class.forName("com.cloudbees.plugins.credentials.common.StandardUsernameCredentials");

            Method lookup = null;
            for (Method m : cpClass.getMethods()) {
                if ("lookupCredentials".equals(m.getName()) && m.getParameterCount() == 4) {
                    lookup = m;
                    break;
                }
            }
            if (lookup != null) {
                Object credsList = lookup.invoke(null, credClass, null, null, Collections.emptyList());
                if (credsList instanceof List) {
                    for (Object c : (List<?>) credsList) {
                        Method getId = findMethod(c.getClass(), "getId");
                        if (getId != null && credentialsId.equals(getId.invoke(c))) {
                            Method getUsername = findMethod(c.getClass(), "getUsername");
                            if (getUsername != null) {
                                Object u = getUsername.invoke(c);
                                if (u instanceof String && !((String) u).trim().isEmpty()) {
                                    return (String) u;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Failed reflective credentials lookup: " + t.getMessage());
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String methodName) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == 0) {
                return m;
            }
        }
        return null;
    }

    public static void clearCache(String nodeName) {
        if (nodeName != null) {
            RUNTIME_USER_CACHE.remove(nodeName);
        }
    }
}
