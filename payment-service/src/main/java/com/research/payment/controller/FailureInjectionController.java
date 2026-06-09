package com.research.payment.controller;

import com.research.payment.service.FailureInjectionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments/admin")
@RequiredArgsConstructor
public class FailureInjectionController {

    private static final Logger log = LoggerFactory.getLogger(FailureInjectionController.class);
    private final FailureInjectionService failureInjectionService;

    // POST http://localhost:8083/api/payments/admin/inject/gateway-timeout
    @PostMapping("/inject/gateway-timeout")
    public ResponseEntity<Map<String, Object>> injectGatewayTimeout() {
        log.warn("ADMIN_REQUEST | endpoint=/inject/gateway-timeout service=payment-service");
        String result = failureInjectionService.startGatewayTimeout();
        return ResponseEntity.ok(Map.of("status", "injected", "scenario", "gateway-timeout", "detail", result));
    }

    // POST http://localhost:8083/api/payments/admin/inject/high-latency
    // Body: { "delayMs": 8000 }
    @PostMapping("/inject/high-latency")
    public ResponseEntity<Map<String, Object>> injectHighLatency(@RequestBody Map<String, Object> body) {
        int delayMs = Integer.parseInt(body.getOrDefault("delayMs", 8000).toString());
        log.warn("ADMIN_REQUEST | endpoint=/inject/high-latency service=payment-service delayMs={}", delayMs);
        String result = failureInjectionService.startHighLatency(delayMs);
        return ResponseEntity.ok(Map.of("status", "injected", "scenario", "high-latency", "delayMs", delayMs, "detail", result));
    }

    // POST http://localhost:8083/api/payments/admin/recover
    @PostMapping("/recover")
    public ResponseEntity<Map<String, Object>> recover() {
        log.info("ADMIN_REQUEST | endpoint=/recover service=payment-service");
        String result = failureInjectionService.recoverAll();
        return ResponseEntity.ok(Map.of("status", "recovered", "detail", result));
    }

    // GET http://localhost:8083/api/payments/admin/status
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(failureInjectionService.getStatus());
    }
}