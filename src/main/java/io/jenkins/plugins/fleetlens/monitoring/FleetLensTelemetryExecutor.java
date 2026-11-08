package io.jenkins.plugins.fleetlens.monitoring;

import hudson.model.Computer;
import hudson.model.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bounded, non-blocking background executor for agent telemetry probes (RPCs, DNS, OS, Storage, Uptime).
 * Prevents sequential synchronous remoting calls or DNS timeouts from blocking HTTP requests on large fleets.
 */
public final class FleetLensTelemetryExecutor {

    private static final Logger LOGGER = Logger.getLogger(FleetLensTelemetryExecutor.class.getName());

    private static final int POOL_SIZE = 16;
    private static final long DEFAULT_FLEET_WARMUP_BUDGET_MS = 350L;
    private static final long DEFAULT_SINGLE_NODE_WARMUP_MS = 250L;

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(POOL_SIZE, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "fleetlens-telemetry-" + THREAD_COUNTER.getAndIncrement());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    });

    private static final Map<String, CompletableFuture<?>> IN_FLIGHT = new ConcurrentHashMap<>();

    private FleetLensTelemetryExecutor() {}

    /**
     * Submits a background probe task deduplicated by key. If a probe with the same key is already
     * running, returns the existing in-flight future instead of queuing duplicate work.
     */
    @SuppressWarnings("unchecked")
    public static <T> CompletableFuture<T> submitDeduplicated(String key, Callable<T> task) {
        CompletableFuture<?> existing = IN_FLIGHT.get(key);
        if (existing != null) {
            return (CompletableFuture<T>) existing;
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        CompletableFuture<?> prev = IN_FLIGHT.putIfAbsent(key, future);
        if (prev != null) {
            return (CompletableFuture<T>) prev;
        }

        EXECUTOR.submit(() -> {
            try {
                T result = task.call();
                future.complete(result);
            } catch (Throwable t) {
                LOGGER.log(Level.FINE, "Background telemetry probe failed for key: " + key, t);
                future.completeExceptionally(t);
            } finally {
                IN_FLIGHT.remove(key, future);
            }
        });

        return future;
    }

    /**
     * Warms up all cold telemetry caches across an array of computers in parallel, waiting at most
     * {@code maxWaitMs} total across the entire fleet. Any slow or unresponsive agents continue
     * completing in the background without blocking the caller.
     */
    public static void warmupComputers(Computer[] computers) {
        warmupComputers(computers, DEFAULT_FLEET_WARMUP_BUDGET_MS);
    }

    public static void warmupComputers(Computer[] computers, long maxWaitMs) {
        if (computers == null || computers.length == 0) {
            return;
        }

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (Computer computer : computers) {
            if (computer == null) {
                continue;
            }
            Node node = computer.getNode();
            collectWarmupFutures(computer, node, futures);
        }

        awaitFuturesBounded(futures, maxWaitMs);
    }

    /**
     * Warms up cold telemetry caches for a single computer with a short bounded timeout.
     */
    public static void warmupSingleComputer(Computer computer, Node node) {
        if (computer == null) {
            return;
        }
        List<CompletableFuture<?>> futures = new ArrayList<>(4);
        collectWarmupFutures(computer, node, futures);
        awaitFuturesBounded(futures, DEFAULT_SINGLE_NODE_WARMUP_MS);
    }

    private static void collectWarmupFutures(Computer computer, Node node, List<CompletableFuture<?>> futures) {
        CompletableFuture<?> osFuture = AgentOsDetector.warmupAsync(computer);
        if (osFuture != null) {
            futures.add(osFuture);
        }

        CompletableFuture<?> storageFuture = AgentStorageService.warmupAsync(computer, node);
        if (storageFuture != null) {
            futures.add(storageFuture);
        }

        CompletableFuture<?> uptimeFuture = AgentUptimeService.warmupAsync(computer);
        if (uptimeFuture != null) {
            futures.add(uptimeFuture);
        }

        CompletableFuture<?> networkFuture = AgentNetworkService.warmupAsync(computer, node);
        if (networkFuture != null) {
            futures.add(networkFuture);
        }

        CompletableFuture<?> connFuture = AgentConnectionService.warmupAsync(computer, node);
        if (connFuture != null) {
            futures.add(connFuture);
        }
    }

    private static void awaitFuturesBounded(List<CompletableFuture<?>> futures, long maxWaitMs) {
        if (futures.isEmpty() || maxWaitMs <= 0) {
            return;
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                    .get(maxWaitMs, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // Expected when one or more slow agents exceed the bounded warmup budget;
            // those futures continue populating the cache asynchronously in the background.
        }
    }
}
