package com.research.order.controller;

import com.research.order.dto.OrderDtos;
import com.research.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderDtos.OrderResponse> createOrder(
            @Valid @RequestBody OrderDtos.CreateOrderRequest request) {
        log.info("POST /api/orders received | customerId={} productId={}",
                request.getCustomerId(), request.getProductId());
        OrderDtos.OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<OrderDtos.OrderResponse>> getAllOrders() {
        log.debug("GET /api/orders received");
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDtos.OrderResponse> getOrder(@PathVariable Long id) {
        log.debug("GET /api/orders/{} received", id);
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Order service is running");
    }
}
