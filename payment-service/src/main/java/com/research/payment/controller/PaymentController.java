package com.research.payment.controller;

import com.research.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processPayment(@RequestBody Map<String, Object> request) {
        String orderId = (String) request.get("orderId");
        String customerId = (String) request.get("customerId");
        BigDecimal amount = new BigDecimal(request.get("amount").toString());

        log.info("POST /api/payments/process | orderId={} customerId={} amount={}", orderId, customerId, amount);
        return ResponseEntity.ok(paymentService.processPayment(orderId, customerId, amount));
    }

    // Special endpoint for failure injection — lets you control failure rate at runtime
    @PostMapping("/admin/failure-rate")
    public ResponseEntity<Map<String, Object>> setFailureRate(@RequestBody Map<String, Object> request) {
        double rate = Double.parseDouble(request.get("rate").toString());
        log.warn("Admin: payment failure rate update requested | newRate={}", rate);
        paymentService.setFailureRate(rate);
        return ResponseEntity.ok(Map.of("message", "Failure rate updated", "newRate", rate));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Payment service is running");
    }
}
