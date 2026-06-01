package com.research.inventory.controller;

import com.research.inventory.model.Inventory;
import com.research.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    private final InventoryService inventoryService;

    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkInventory(
            @RequestParam String productId,
            @RequestParam int quantity) {
        log.debug("GET /api/inventory/check | productId={} quantity={}", productId, quantity);
        return ResponseEntity.ok(inventoryService.checkAvailability(productId, quantity));
    }

    @PostMapping("/add")
    public ResponseEntity<Inventory> addStock(@RequestBody Map<String, Object> request) {
        String productId = (String) request.get("productId");
        String productName = (String) request.get("productName");
        int quantity = (int) request.get("quantity");
        log.info("POST /api/inventory/add | productId={} quantity={}", productId, quantity);
        return ResponseEntity.ok(inventoryService.addStock(productId, productName, quantity));
    }

    @PostMapping("/reduce")
    public ResponseEntity<Map<String, Object>> reduceStock(@RequestBody Map<String, Object> request) {
        String productId = (String) request.get("productId");
        int quantity = (int) request.get("quantity");
        log.info("POST /api/inventory/reduce | productId={} quantity={}", productId, quantity);
        boolean success = inventoryService.reduceStock(productId, quantity);
        return ResponseEntity.ok(Map.of("success", success, "productId", productId));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Inventory service is running");
    }
}
