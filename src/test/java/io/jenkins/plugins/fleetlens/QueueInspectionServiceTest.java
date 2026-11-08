package io.jenkins.plugins.fleetlens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Queue;
import hudson.slaves.DumbSlave;
import io.jenkins.plugins.fleetlens.api.dto.QueueItemDto;
import io.jenkins.plugins.fleetlens.api.dto.QueueOverviewDto;
import io.jenkins.plugins.fleetlens.queue.QueueInspectionService;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class QueueInspectionServiceTest {

    @Test
    void testEmptyQueue(JenkinsRule j) {
        QueueOverviewDto overview = QueueInspectionService.inspectQueue();
        assertNotNull(overview);
        assertEquals(0, overview.getTotalWaiting());
        assertTrue(overview.getItems().isEmpty());
    }

    @Test
    void testQueueItemWithMissingLabel(JenkinsRule j) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("missing-label-job");
        p.setAssignedLabel(Label.get("nonexistent-hardware-spec"));
        p.scheduleBuild2(0);

        // Allow item to enter queue
        Queue queue = j.jenkins.getQueue();
        assertFalse(queue.isEmpty());

        QueueOverviewDto overview = QueueInspectionService.inspectQueue();
        assertNotNull(overview);
        assertEquals(1, overview.getTotalWaiting());

        QueueItemDto item = overview.getItems().get(0);
        assertEquals("missing-label-job", item.getName());
        assertEquals("nonexistent-hardware-spec", item.getAssignedLabel());
        assertEquals("NO_MATCHING_NODES", item.getBottleneckReason());
        assertEquals(0, item.getMatchingNodesCount());
        assertTrue(item.getBottleneckMessage().contains("No machines configured with label"));

        // Clean up queue
        assertTrue(QueueInspectionService.cancelItem(item.getId()));
        assertTrue(j.jenkins.getQueue().isEmpty());
    }

    @Test
    void testQueueItemWithOfflineAgent(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createSlave(Label.get("isolated-test-pool"));
        // slave is created offline (not online)
        assertFalse(slave.toComputer().isOnline());

        FreeStyleProject p = j.createFreeStyleProject("offline-targeted-job");
        p.setAssignedLabel(Label.get("isolated-test-pool"));
        p.scheduleBuild2(0);

        QueueOverviewDto overview = QueueInspectionService.inspectQueue();
        assertNotNull(overview);
        assertEquals(1, overview.getTotalWaiting());

        QueueItemDto item = overview.getItems().get(0);
        assertEquals("isolated-test-pool", item.getAssignedLabel());
        assertEquals("ALL_NODES_OFFLINE", item.getBottleneckReason());
        assertEquals(1, item.getMatchingNodesCount());
        assertEquals(0, item.getOnlineMatchingNodesCount());
        assertTrue(item.getBottleneckMessage().contains("OFFLINE"));

        // Cancel item
        assertTrue(QueueInspectionService.cancelItem(item.getId()));
    }

    @Test
    void testMultipleQueueItemsMemoization(JenkinsRule j) throws Exception {
        Label testLabel = Label.get("shared-compute-pool");
        FreeStyleProject p1 = j.createFreeStyleProject("shared-job-1");
        p1.setAssignedLabel(testLabel);
        p1.scheduleBuild2(0);

        FreeStyleProject p2 = j.createFreeStyleProject("shared-job-2");
        p2.setAssignedLabel(testLabel);
        p2.scheduleBuild2(0);

        QueueOverviewDto overview = QueueInspectionService.inspectQueue();
        assertNotNull(overview);
        assertEquals(2, overview.getTotalWaiting());

        QueueItemDto item1 = overview.getItems().get(0);
        QueueItemDto item2 = overview.getItems().get(1);

        assertEquals("shared-compute-pool", item1.getAssignedLabel());
        assertEquals("shared-compute-pool", item2.getAssignedLabel());
        assertEquals("NO_MATCHING_NODES", item1.getBottleneckReason());
        assertEquals("NO_MATCHING_NODES", item2.getBottleneckReason());
        assertEquals(item1.getBottleneckMessage(), item2.getBottleneckMessage());

        assertTrue(QueueInspectionService.cancelItem(item1.getId()));
        assertTrue(QueueInspectionService.cancelItem(item2.getId()));
    }
}
