package com.research.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class FailureInjectionService {

    private static final Logger log = LoggerFactory.getLogger(FailureInjectionService.class);

    @Autowired
    private DataSource dataSource;

    private final AtomicBoolean dbPoolExhaustActive = new AtomicBoolean(false);
    private final AtomicBoolean slowQueryActive     = new AtomicBoolean(false);
    private final List<Connection> heldConnections  = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private ScheduledFuture<?> dbPoolLogTask;
    private ScheduledFuture<?> slowQueryLogTask;
    private int slowQueryDelayMs = 0;

    // ── 1. DB CONNECTION POOL EXHAUSTION ────────────────────────
    // Grabs and holds DB connections without releasing them.
    // New requests cannot get a connection → timeouts cascade through all services.
    public String startDbPoolExhaustion() {
        if (dbPoolExhaustActive.get()) {
            return "DB pool exhaustion already active";
        }
        dbPoolExhaustActive.set(true);
        log.warn("FAILURE_INJECTION | type=DB_POOL_EXHAUSTION status=STARTED | " +
                "Holding DB connections open to exhaust pool");

        // Grab connections in a background thread until pool is exhausted
        scheduler.submit(() -> {
            int count = 0;
            while (dbPoolExhaustActive.get()) {
                try {
                    Connection conn = dataSource.getConnection();
                    synchronized (heldConnections) {
                        heldConnections.add(conn);
                    }
                    count++;
                    log.warn("FAILURE_INJECTION | type=DB_POOL_EXHAUSTION status=PROGRESSING | " +
                            "heldConnections={}", count);
                    Thread.sleep(500);
                } catch (Exception e) {
                    // Pool exhausted — now log timeouts on each attempt
                    log.error("FAILURE_INJECTION | type=DB_POOL_EXHAUSTION status=POOL_FULL | " +
                                    "error=Cannot_acquire_connection heldConnections={} message={}",
                            count, e.getMessage());
                    try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        });

        // Periodic status log for ML feature window
        dbPoolLogTask = scheduler.scheduleAtFixedRate(() -> {
            if (!dbPoolExhaustActive.get()) return;
            synchronized (heldConnections) {
                log.error("FAILURE_INJECTION | type=DB_POOL_EXHAUSTION status=CRITICAL | " +
                                "heldConnections={} — inventory DB unavailable",
                        heldConnections.size());
            }
        }, 5, 5, TimeUnit.SECONDS);

        return "DB pool exhaustion started — holding connections open";
    }

    public String stopDbPoolExhaustion() {
        dbPoolExhaustActive.set(false);
        if (dbPoolLogTask != null) dbPoolLogTask.cancel(false);
        synchronized (heldConnections) {
            for (Connection conn : heldConnections) {
                try { conn.close(); } catch (Exception ignored) {}
            }
            heldConnections.clear();
        }
        log.info("FAILURE_INJECTION | type=DB_POOL_EXHAUSTION status=STOPPED | All connections released");
        return "DB pool exhaustion stopped — connections released";
    }

    // ── 2. SLOW QUERY SIMULATION ────────────────────────────────
    // Adds artificial delay before every DB operation.
    // Simulates slow queries / DB under load — latency cascades to Order Service.
    public String startSlowQuery(int delayMs) {
        this.slowQueryDelayMs = delayMs;
        slowQueryActive.set(true);
        log.warn("FAILURE_INJECTION | type=SLOW_QUERY status=STARTED | " +
                "queryDelayMs={}", delayMs);

        slowQueryLogTask = scheduler.scheduleAtFixedRate(() -> {
            if (!slowQueryActive.get()) return;
            log.warn("FAILURE_INJECTION | type=SLOW_QUERY status=PROGRESSING | " +
                    "queryDelayMs={} — all DB queries degraded", slowQueryDelayMs);
        }, 10, 10, TimeUnit.SECONDS);

        return "Slow query injection started — adding " + delayMs + "ms to every DB call";
    }

    public String stopSlowQuery() {
        slowQueryActive.set(false);
        slowQueryDelayMs = 0;
        if (slowQueryLogTask != null) slowQueryLogTask.cancel(false);
        log.info("FAILURE_INJECTION | type=SLOW_QUERY status=STOPPED");
        return "Slow query injection stopped";
    }

    // ── Used by InventoryService ─────────────────────────────────
    public void applySlowQueryIfActive() {
        if (slowQueryActive.get() && slowQueryDelayMs > 0) {
            try {
                log.debug("Applying slow query delay | delayMs={}", slowQueryDelayMs);
                Thread.sleep(slowQueryDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public java.util.Map<String, Object> getStatus() {
        return java.util.Map.of(
                "dbPoolExhaustionActive", dbPoolExhaustActive.get(),
                "slowQueryActive",        slowQueryActive.get(),
                "slowQueryDelayMs",       slowQueryDelayMs,
                "heldConnectionCount",    heldConnections.size()
        );
    }

    public String recoverAll() {
        stopDbPoolExhaustion();
        stopSlowQuery();
        log.info("FAILURE_INJECTION | type=ALL status=RECOVERED | Inventory service recovering");
        return "All failure injections stopped";
    }
}