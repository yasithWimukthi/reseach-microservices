package com.research.inventory.service;

import com.research.inventory.model.Inventory;
import com.research.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;

    private final FailureInjectionService failureInjectionService;

    public Map<String, Object> checkAvailability(String productId, int quantity) {
        long start = System.currentTimeMillis();

        failureInjectionService.applySlowQueryIfActive();
        log.info("Inventory check request | productId={} requestedQty={}", productId, quantity);

        try {
            Inventory inventory = inventoryRepository.findByProductId(productId)
                    .orElse(null);

            if (inventory == null) {
                log.warn("Product not found in inventory | productId={}", productId);
                return Map.of("available", false, "reason", "Product not found", "productId", productId);
            }

            int available = inventory.getAvailableQuantity();
            boolean isAvailable = available >= quantity;

            long duration = System.currentTimeMillis() - start;
            log.info("Inventory check result | productId={} requestedQty={} availableQty={} result={} durationMs={}",
                    productId, quantity, available, isAvailable, duration);

            return Map.of(
                    "available", isAvailable,
                    "productId", productId,
                    "requestedQuantity", quantity,
                    "availableQuantity", available
            );

        } catch (Exception e) {
            log.error("Inventory check error | productId={} error={}", productId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public Inventory addStock(String productId, String productName, int quantity) {
        log.info("Adding stock | productId={} productName={} quantity={}", productId, productName, quantity);

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElse(Inventory.builder()
                        .productId(productId)
                        .productName(productName)
                        .reservedQuantity(0)
                        .quantity(0)
                        .build());

        int previousQty = inventory.getQuantity();
        inventory.setQuantity(inventory.getQuantity() + quantity);
        inventory.setUpdatedAt(LocalDateTime.now());
        inventory = inventoryRepository.save(inventory);

        log.info("Stock updated | productId={} previousQty={} newQty={} addedQty={}",
                productId, previousQty, inventory.getQuantity(), quantity);

        return inventory;
    }

    @Transactional
    public boolean reduceStock(String productId, int quantity) {
        log.info("Reducing stock | productId={} quantity={}", productId, quantity);

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> {
                    log.error("Product not found for stock reduction | productId={}", productId);
                    return new RuntimeException("Product not found: " + productId);
                });

        if (inventory.getAvailableQuantity() < quantity) {
            log.warn("Insufficient stock for reduction | productId={} available={} requested={}",
                    productId, inventory.getAvailableQuantity(), quantity);
            return false;
        }

        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryRepository.save(inventory);

        log.info("Stock reduced | productId={} reducedBy={} remaining={}",
                productId, quantity, inventory.getQuantity());

        return true;
    }
}
