package com.research.inventory.controller;

import com.research.inventory.service.FailureInjectionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory/admin")
@RequiredArgsConstructor
public class FailureInjectionController {

    private static final Logger log = LoggerFactory.getLogger(FailureInjectionController.class);
    private final FailureInjectionService failureInjectionService;

    // POST http://localhost:8082/api/inventory/admin/inject/db-pool-exhaustion
    @PostMapping("/inject/db-pool-exhaustion")
    public ResponseEntity<Map<String, Object>> injectDbPoolExhaustion() {
        log.warn("ADMIN_REQUEST | endpoint=/inject/db-pool-exhaustion service=inventory-service");
        String result = failureInjectionService.startDbPoolExhaustion();
        return ResponseEntity.ok(Map.of("status", "injected", "scenario", "db-pool-exhaustion", "detail", result));
    }

    // POST http://localhost:8082/api/inventory/admin/inject/slow-query
    // Body: { "delayMs": 5000 }
    @PostMapping("/inject/slow-query")
    public ResponseEntity<Map<String, Object>> injectSlowQuery(@RequestBody Map<String, Object> body) {
        int delayMs = Integer.parseInt(body.getOrDefault("delayMs", 5000).toString());
        log.warn("ADMIN_REQUEST | endpoint=/inject/slow-query service=inventory-service delayMs={}", delayMs);
        String result = failureInjectionService.startSlowQuery(delayMs);
        return ResponseEntity.ok(Map.of("status", "injected", "scenario", "slow-query", "delayMs", delayMs, "detail", result));
    }

    // POST http://localhost:8082/api/inventory/admin/recover
    @PostMapping("/recover")
    public ResponseEntity<Map<String, Object>> recover() {
        log.info("ADMIN_REQUEST | endpoint=/recover service=inventory-service");
        String result = failureInjectionService.recoverAll();
        return ResponseEntity.ok(Map.of("status", "recovered", "detail", result));
    }

    // GET http://localhost:8082/api/inventory/admin/status
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(failureInjectionService.getStatus());
    }
}