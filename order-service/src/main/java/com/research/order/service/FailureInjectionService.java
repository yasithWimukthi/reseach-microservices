package com.research.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class FailureInjectionService {

    private static final Logger log = LoggerFactory.getLogger(FailureInjectionService.class);

    // ── Shared state flags ──────────────────────────────────────
    private final AtomicBoolean memoryLeakActive    = new AtomicBoolean(false);
    private final AtomicBoolean cpuOverloadActive   = new AtomicBoolean(false);
    private final AtomicBoolean slowResponseActive  = new AtomicBoolean(false);

    private final List<byte[]>  memoryLeakStore     = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    private ScheduledFuture<?> memoryLeakTask;
    private ScheduledFuture<?> cpuOverloadTask;
    private ScheduledFuture<?> slowResponseLogTask;

    private int slowResponseDelayMs = 0;

    // ── 1. MEMORY LEAK ──────────────────────────────────────────
    // Gradually allocates memory every 10 seconds.
    // Logs heap usage so the ML model can detect the rising trend.
    public String startMemoryLeak() {
        if (memoryLeakActive.get()) {
            return "Memory leak already active";
        }
        memoryLeakActive.set(true);
        log.warn("FAILURE_INJECTION | type=MEMORY_LEAK status=STARTED | " +
                "Allocating 20MB every 10s until heap exhaustion");

        memoryLeakTask = scheduler.scheduleAtFixedRate(() -> {
            if (!memoryLeakActive.get()) return;
            try {
                // Allocate 20MB chunk and hold reference so GC cannot collect it
                memoryLeakStore.add(new byte[20 * 1024 * 1024]);

                MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
                long usedHeap  = memBean.getHeapMemoryUsage().getUsed()  / (1024 * 1024);
                long maxHeap   = memBean.getHeapMemoryUsage().getMax()   / (1024 * 1024);
                double heapPct = (double) usedHeap / maxHeap * 100;

                log.warn("FAILURE_INJECTION | type=MEMORY_LEAK status=PROGRESSING | " +
                                "heapUsedMb={} heapMaxMb={} heapUsedPct={} allocatedChunks={}",
                        usedHeap, maxHeap, String.format("%.1f", heapPct),
                        memoryLeakStore.size());

                // Escalate log level as heap fills — gives ML model a clear signal ramp
                if (heapPct > 70) {
                    log.error("FAILURE_INJECTION | type=MEMORY_LEAK status=CRITICAL | " +
                            "heapUsedPct={} — OOM imminent", String.format("%.1f", heapPct));
                }
            } catch (OutOfMemoryError oom) {
                log.error("FAILURE_INJECTION | type=MEMORY_LEAK status=OOM_REACHED | " +
                        "error={}", oom.getMessage());
                memoryLeakActive.set(false);
            }
        }, 0, 10, TimeUnit.SECONDS);

        return "Memory leak injection started — allocating 20MB every 10 seconds";
    }

    public String stopMemoryLeak() {
        memoryLeakActive.set(false);
        if (memoryLeakTask != null) memoryLeakTask.cancel(false);
        memoryLeakStore.clear();
        System.gc();
        log.info("FAILURE_INJECTION | type=MEMORY_LEAK status=STOPPED | Memory released");
        return "Memory leak stopped and memory released";
    }

    // ── 2. CPU OVERLOAD ─────────────────────────────────────────
    // Spins busy threads to pin CPU usage above 90%.
    // Logs CPU load every 5 seconds for ML feature extraction.
    public String startCpuOverload() {
        if (cpuOverloadActive.get()) {
            return "CPU overload already active";
        }
        cpuOverloadActive.set(true);
        int cpuCores = Runtime.getRuntime().availableProcessors();
        log.warn("FAILURE_INJECTION | type=CPU_OVERLOAD status=STARTED | " +
                "Spinning {} busy threads (cores={})", cpuCores, cpuCores);

        // Spin one busy thread per CPU core
        for (int i = 0; i < cpuCores; i++) {
            final int threadId = i;
            scheduler.submit(() -> {
                log.debug("CPU overload thread started | threadId={}", threadId);
                while (cpuOverloadActive.get()) {
                    // Pure busy-wait — keeps core at 100%
                    Math.sqrt(Math.random() * Long.MAX_VALUE);
                }
                log.debug("CPU overload thread stopped | threadId={}", threadId);
            });
        }

        // Separate logging task so dashboard/ML can see CPU trend
        cpuOverloadTask = scheduler.scheduleAtFixedRate(() -> {
            if (!cpuOverloadActive.get()) return;
            com.sun.management.OperatingSystemMXBean osBean =
                    (com.sun.management.OperatingSystemMXBean)
                            ManagementFactory.getOperatingSystemMXBean();
            double cpuLoad = osBean.getProcessCpuLoad() * 100;
            log.warn("FAILURE_INJECTION | type=CPU_OVERLOAD status=PROGRESSING | " +
                            "cpuLoadPct={} activeThreads={}",
                    String.format("%.1f", cpuLoad),
                    Thread.activeCount());
        }, 5, 5, TimeUnit.SECONDS);

        return "CPU overload injection started — " + cpuCores + " busy threads spinning";
    }

    public String stopCpuOverload() {
        cpuOverloadActive.set(false);
        if (cpuOverloadTask != null) cpuOverloadTask.cancel(false);
        log.info("FAILURE_INJECTION | type=CPU_OVERLOAD status=STOPPED");
        return "CPU overload stopped";
    }

    // ── 3. SLOW RESPONSE ────────────────────────────────────────
    // Injects artificial latency into every order request.
    // Models gradual network degradation / resource contention.
    public String startSlowResponse(int delayMs) {
        this.slowResponseDelayMs = delayMs;
        slowResponseActive.set(true);
        log.warn("FAILURE_INJECTION | type=SLOW_RESPONSE status=STARTED | " +
                "artificialDelayMs={}", delayMs);

        // Periodic log so ML model sees latency trend even between requests
        slowResponseLogTask = scheduler.scheduleAtFixedRate(() -> {
            if (!slowResponseActive.get()) return;
            log.warn("FAILURE_INJECTION | type=SLOW_RESPONSE status=PROGRESSING | " +
                            "artificialDelayMs={} — all order requests degraded",
                    slowResponseDelayMs);
        }, 10, 10, TimeUnit.SECONDS);

        return "Slow response injection started — adding " + delayMs + "ms to every request";
    }

    public String stopSlowResponse() {
        slowResponseActive.set(false);
        slowResponseDelayMs = 0;
        if (slowResponseLogTask != null) slowResponseLogTask.cancel(false);
        log.info("FAILURE_INJECTION | type=SLOW_RESPONSE status=STOPPED");
        return "Slow response injection stopped";
    }

    // ── Used by OrderService to apply delay if active ───────────
    public void applySlowResponseIfActive() {
        if (slowResponseActive.get() && slowResponseDelayMs > 0) {
            try {
                log.debug("Applying artificial delay | delayMs={}", slowResponseDelayMs);
                Thread.sleep(slowResponseDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ── STATUS ───────────────────────────────────────────────────
    public java.util.Map<String, Object> getStatus() {
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        long usedMb = memBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long maxMb  = memBean.getHeapMemoryUsage().getMax()  / (1024 * 1024);
        com.sun.management.OperatingSystemMXBean osBean =
                (com.sun.management.OperatingSystemMXBean)
                        ManagementFactory.getOperatingSystemMXBean();

        return java.util.Map.of(
                "memoryLeakActive",   memoryLeakActive.get(),
                "cpuOverloadActive",  cpuOverloadActive.get(),
                "slowResponseActive", slowResponseActive.get(),
                "slowResponseDelayMs", slowResponseDelayMs,
                "heapUsedMb",         usedMb,
                "heapMaxMb",          maxMb,
                "cpuLoadPct",         String.format("%.1f", osBean.getProcessCpuLoad() * 100),
                "activeThreads",      Thread.activeCount()
        );
    }

    // ── RECOVER ALL ──────────────────────────────────────────────
    public String recoverAll() {
        stopMemoryLeak();
        stopCpuOverload();
        stopSlowResponse();
        log.info("FAILURE_INJECTION | type=ALL status=RECOVERED | All injections stopped");
        return "All failure injections stopped — service recovering to normal";
    }
}