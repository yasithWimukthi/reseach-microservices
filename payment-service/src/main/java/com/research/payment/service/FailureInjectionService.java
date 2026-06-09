package com.research.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FailureInjectionService {

    private static final Logger log = LoggerFactory.getLogger(FailureInjectionService.class);

    private final AtomicBoolean  gatewayTimeoutActive  = new AtomicBoolean(false);
    private final AtomicBoolean  highLatencyActive     = new AtomicBoolean(false);
    private final AtomicInteger  timeoutCount          = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler    = Executors.newScheduledThreadPool(2);

    private ScheduledFuture<?> gatewayLogTask;
    private ScheduledFuture<?> latencyLogTask;
    private int highLatencyDelayMs = 0;

    // ── 1. GATEWAY TIMEOUT ───────────────────────────────────────
    // Simulates the external payment gateway becoming unresponsive.
    // Every payment request will timeout after a long wait.
    // This is different from the failure-rate toggle — this causes
    // timeouts (which generate very different log patterns than declines).
    public String startGatewayTimeout() {
        if (gatewayTimeoutActive.get()) {
            return "Gateway timeout already active";
        }
        gatewayTimeoutActive.set(true);
        timeoutCount.set(0);
        log.warn("FAILURE_INJECTION | type=GATEWAY_TIMEOUT status=STARTED | " +
                "Payment gateway simulated as unresponsive");

        gatewayLogTask = scheduler.scheduleAtFixedRate(() -> {
            if (!gatewayTimeoutActive.get()) return;
            log.error("FAILURE_INJECTION | type=GATEWAY_TIMEOUT status=PROGRESSING | " +
                            "totalTimeouts={} — payment gateway not responding",
                    timeoutCount.get());
        }, 5, 5, TimeUnit.SECONDS);

        return "Gateway timeout injection started — all payments will timeout";
    }

    public String stopGatewayTimeout() {
        gatewayTimeoutActive.set(false);
        if (gatewayLogTask != null) gatewayLogTask.cancel(false);
        log.info("FAILURE_INJECTION | type=GATEWAY_TIMEOUT status=STOPPED | totalTimeouts={}",
                timeoutCount.get());
        return "Gateway timeout stopped";
    }

    // Called by PaymentService on each request
    public  void applyGatewayTimeoutIfActive() throws InterruptedException {
        if (gatewayTimeoutActive.get()) {
            int count = timeoutCount.incrementAndGet();
            log.error("FAILURE_INJECTION | type=GATEWAY_TIMEOUT status=TIMEOUT_OCCURRING | " +
                            "timeoutNumber={} — waiting 30s for gateway response",
                    count);
            // Simulate a 30-second timeout (in real demo, RestTemplate will cut it off at its timeout limit)
            Thread.sleep(30_000);
        }
    }

    // ── 2. HIGH LATENCY ──────────────────────────────────────────
    // Adds increasing delay to every payment — simulates degraded gateway.
    // Different from gateway timeout: requests complete but very slowly.
    public String startHighLatency(int delayMs) {
        this.highLatencyDelayMs = delayMs;
        highLatencyActive.set(true);
        log.warn("FAILURE_INJECTION | type=HIGH_LATENCY status=STARTED | " +
                "gatewayLatencyMs={}", delayMs);

        latencyLogTask = scheduler.scheduleAtFixedRate(() -> {
            if (!highLatencyActive.get()) return;
            log.warn("FAILURE_INJECTION | type=HIGH_LATENCY status=PROGRESSING | " +
                            "gatewayLatencyMs={} — payment gateway degraded",
                    highLatencyDelayMs);
        }, 10, 10, TimeUnit.SECONDS);

        return "High latency injection started — adding " + delayMs + "ms to every payment";
    }

    public String stopHighLatency() {
        highLatencyActive.set(false);
        highLatencyDelayMs = 0;
        if (latencyLogTask != null) latencyLogTask.cancel(false);
        log.info("FAILURE_INJECTION | type=HIGH_LATENCY status=STOPPED");
        return "High latency injection stopped";
    }

    public void applyHighLatencyIfActive() {
        if (highLatencyActive.get() && highLatencyDelayMs > 0) {
            try {
                log.debug("Applying gateway latency | delayMs={}", highLatencyDelayMs);
                Thread.sleep(highLatencyDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public java.util.Map<String, Object> getStatus() {
        return java.util.Map.of(
                "gatewayTimeoutActive", gatewayTimeoutActive.get(),
                "highLatencyActive",    highLatencyActive.get(),
                "highLatencyDelayMs",   highLatencyDelayMs,
                "totalTimeouts",        timeoutCount.get()
        );
    }

    public String recoverAll() {
        stopGatewayTimeout();
        stopHighLatency();
        log.info("FAILURE_INJECTION | type=ALL status=RECOVERED | Payment service recovering");
        return "All failure injections stopped";
    }
}