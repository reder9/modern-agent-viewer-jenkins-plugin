package io.jenkins.plugins.fleetlens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.fleetlens.monitoring.AgentStorageService;
import io.jenkins.plugins.fleetlens.monitoring.AgentUptimeService;
import org.junit.jupiter.api.Test;

class AgentStorageAndUptimeTest {

    @Test
    void testFormatBytes() {
        assertEquals("--", AgentStorageService.formatBytes(-1));
        assertEquals("500 B", AgentStorageService.formatBytes(500));
        assertEquals("1.0 KB", AgentStorageService.formatBytes(1024));
        assertEquals("1.5 MB", AgentStorageService.formatBytes((long) (1.5 * 1024 * 1024)));
        assertEquals("50.0 GB", AgentStorageService.formatBytes(50L * 1024 * 1024 * 1024));
        assertEquals("2.0 TB", AgentStorageService.formatBytes(2L * 1024 * 1024 * 1024 * 1024));
    }

    @Test
    void testFormatDuration() {
        assertEquals("--", AgentUptimeService.formatDuration(-1));
        assertEquals("12s", AgentUptimeService.formatDuration(12_000));
        assertEquals("5m 30s", AgentUptimeService.formatDuration(5 * 60 * 1000 + 30 * 1000));
        assertEquals("3h 15m", AgentUptimeService.formatDuration(3 * 3600 * 1000 + 15 * 60 * 1000));
        assertEquals("2d 4h", AgentUptimeService.formatDuration(2 * 86400 * 1000 + 4 * 3600 * 1000));
    }
}
