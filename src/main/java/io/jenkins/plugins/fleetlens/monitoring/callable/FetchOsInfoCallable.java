package io.jenkins.plugins.fleetlens.monitoring.callable;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jenkins.security.MasterToSlaveCallable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Callable executed on the agent to detect deep OS distribution details (e.g. Linux distros from /etc/os-release).
 */
public class FetchOsInfoCallable extends MasterToSlaveCallable<String, RuntimeException> {

    private static final long serialVersionUID = 1L;

    @Override
    @SuppressFBWarnings(value = "DMI_HARDCODED_ABSOLUTE_FILENAME", justification = "Probing standard Linux os-release files")
    public String call() {
        // Check /etc/os-release or /usr/lib/os-release
        File[] releaseFiles = new File[] {
                new File("/etc/os-release"),
                new File("/usr/lib/os-release"),
                new File("/etc/redhat-release"),
                new File("/etc/system-release")
        };

        for (File file : releaseFiles) {
            if (file.exists() && file.isFile() && file.canRead()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                    String line;
                    String prettyName = null;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("PRETTY_NAME=")) {
                            prettyName = line.substring("PRETTY_NAME=".length()).replace("\"", "").replace("'", "").trim();
                            break;
                        }
                    }
                    if (prettyName != null && !prettyName.isEmpty()) {
                        return prettyName;
                    }
                } catch (IOException ignored) {
                    // Try next file
                }
            }
        }
        return null;
    }
}
