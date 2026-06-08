package com.research.order.controller;

import com.research.order.service.FailureInjectionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders/admin")
@RequiredArgsConstructor
public class FailureInjectionController {

    private static final Logger log = LoggerFactory.getLogger(FailureInjectionController.class);
    private final FailureInjectionService failureInjectionService;

    // POST http://localhost:8081/api/orders/admin/inject/memory-leak
    @PostMapping("/inject/memory-leak")
    public ResponseEntity<Map<String, Object>> injectMemoryLeak() {
        log.warn("ADMIN_REQUEST | endpoint=/inject/memory-leak service=order-service");
        String result = failureInjectionService.startMemoryLeak();
        return ResponseEntity.ok(Map.of("status", "injected", "scenario", "memory-leak", "detail", result));
    }

    // POST http://localhost:8081/api/orders/admin/inject/cpu-overload
    @PostMapping("/inject/cpu-overload")
    public ResponseEntity<Map<String, Object>> injectCpuOverload() {
        log.warn("ADMIN_REQUEST | endpoint=/inject/cpu-overload service=order-service");
        String result = failureInjectionService.startCpuOverload();
        return ResponseEntity.ok(Map.of("status", "injected", "scenario", "cpu-overload", "detail", result));
    }

    // POST http://localhost:8081/api/orders/admin/inject/slow-response
    // Body: { "delayMs": 3000 }
    @PostMapping("/inject/slow-response")
    public ResponseEntity<Map<String, Object>> injectSlowResponse(@RequestBody Map<String, Object> body) {
        int delayMs = Integer.parseInt(body.getOrDefault("delayMs", 3000).toString());
        log.warn("ADMIN_REQUEST | endpoint=/inject/slow-response service=order-service delayMs={}", delayMs);
        String result = failureInjectionService.startSlowResponse(delayMs);
        return ResponseEntity.ok(Map.of("status", "injected", "scenario", "slow-response", "delayMs", delayMs, "detail", result));
    }

    // POST http://localhost:8081/api/orders/admin/recover
    @PostMapping("/recover")
    public ResponseEntity<Map<String, Object>> recover() {
        log.info("ADMIN_REQUEST | endpoint=/recover service=order-service");
        String result = failureInjectionService.recoverAll();
        return ResponseEntity.ok(Map.of("status", "recovered", "detail", result));
    }

    // GET http://localhost:8081/api/orders/admin/status
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(failureInjectionService.getStatus());
    }
}