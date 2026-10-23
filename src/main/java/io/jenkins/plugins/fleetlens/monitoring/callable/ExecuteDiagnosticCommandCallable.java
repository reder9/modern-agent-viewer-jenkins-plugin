package io.jenkins.plugins.fleetlens.monitoring.callable;

import jenkins.security.MasterToSlaveCallable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Callable executed over the Jenkins Remoting Channel to safely execute an ad-hoc diagnostic command.
 * Strictly bounded by an execution timeout and output buffer limit.
 */
public class ExecuteDiagnosticCommandCallable extends MasterToSlaveCallable<DiagnosticResult, IOException> {

    private static final long serialVersionUID = 1L;
    private static final int MAX_OUTPUT_CHARS = 256 * 1024; // 256 KB cap

    private final String command;
    private final int timeoutSeconds;
    private final boolean isWindows;

    public ExecuteDiagnosticCommandCallable(String command, int timeoutSeconds, boolean isWindows) {
        this.command = command != null ? command : "";
        this.timeoutSeconds = (timeoutSeconds > 0) ? Math.min(timeoutSeconds, 60) : 15;
        this.isWindows = isWindows;
    }

    @Override
    public DiagnosticResult call() throws IOException {
        long startTime = System.currentTimeMillis();

        ProcessBuilder pb;
        if (isWindows) {
            String trimmed = command.trim();
            if (trimmed.startsWith("cmd ") || trimmed.startsWith("cmd.exe ") || trimmed.startsWith("cmd /c ") || trimmed.startsWith("cmd.exe /c ")) {
                pb = new ProcessBuilder("cmd.exe", "/c", trimmed);
            } else {
                // Execute in PowerShell - supports PowerShell cmdlets, POSIX aliases (ls, ps, cat, rm, mv),
                // system tools (ipconfig, ping, netstat, tasklist), and script blocks.
                pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", trimmed);
            }
        } else {
            pb = new ProcessBuilder("/bin/sh", "-c", command);
        }
        pb.redirectErrorStream(true);

        Process process = pb.start();
        final StringBuilder output = new StringBuilder();
        boolean timedOut = false;
        int exitCode = -1;

        // Asynchronously read stdout/stderr to prevent process buffer deadlocks
        Thread readerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    synchronized (output) {
                        if (output.length() < MAX_OUTPUT_CHARS) {
                            int toAppend = Math.min(read, MAX_OUTPUT_CHARS - output.length());
                            output.append(buffer, 0, toAppend);
                            if (output.length() >= MAX_OUTPUT_CHARS) {
                                output.append("\n[Output truncated: exceeded 256 KB buffer limit]");
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();

        try {
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (finished) {
                exitCode = process.exitValue();
                readerThread.join(1000);
            } else {
                timedOut = true;
                process.destroyForcibly();
                exitCode = -1;
                synchronized (output) {
                    output.append("\n[Execution terminated: command exceeded timeout of ")
                          .append(timeoutSeconds)
                          .append(" seconds]");
                }
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            synchronized (output) {
                output.append("\n[Execution interrupted by controller]");
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        String finalOutput;
        synchronized (output) {
            finalOutput = output.toString();
        }

        return new DiagnosticResult(command, exitCode, finalOutput, durationMs, timedOut);
    }
}
