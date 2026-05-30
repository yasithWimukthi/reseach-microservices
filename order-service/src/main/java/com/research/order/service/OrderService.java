package com.research.order.service;

import com.research.order.dto.OrderDtos;
import com.research.order.model.Order;
import com.research.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);


    private final FailureInjectionService failureInjectionService;

    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;

    @Value("${services.inventory.url}")
    private String inventoryServiceUrl;

    @Value("${services.payment.url}")
    private String paymentServiceUrl;

    public OrderDtos.OrderResponse createOrder(OrderDtos.CreateOrderRequest request) {
        String requestId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        failureInjectionService.applySlowResponseIfActive();

        // Set MDC context for all logs in this request
        MDC.put("requestId", requestId);
        MDC.put("customerId", request.getCustomerId());
        MDC.put("productId", request.getProductId());

        log.info("Order creation started | requestId={} customerId={} productId={} quantity={} amount={}",
                requestId, request.getCustomerId(), request.getProductId(),
                request.getQuantity(), request.getTotalAmount());

        try {
            // Step 1: Check inventory
            boolean inventoryAvailable = checkInventory(request.getProductId(), request.getQuantity(), requestId);
            if (!inventoryAvailable) {
                log.warn("Order failed - insufficient inventory | requestId={} productId={} requestedQty={}",
                        requestId, request.getProductId(), request.getQuantity());
                throw new RuntimeException("Insufficient inventory for product: " + request.getProductId());
            }

            // Step 2: Save order as PENDING
            Order order = Order.builder()
                    .customerId(request.getCustomerId())
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .totalAmount(request.getTotalAmount())
                    .status(Order.OrderStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .build();

            order = orderRepository.save(order);
            log.info("Order saved as PENDING | requestId={} orderId={}", requestId, order.getId());

            // Step 3: Process payment
            boolean paymentSuccess = processPayment(order, requestId);
            if (!paymentSuccess) {
                order.setStatus(Order.OrderStatus.FAILED);
                order.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(order);
                log.error("Order failed - payment rejected | requestId={} orderId={} amount={}",
                        requestId, order.getId(), request.getTotalAmount());
                throw new RuntimeException("Payment failed for order: " + order.getId());
            }

            // Step 4: Confirm order
            order.setStatus(Order.OrderStatus.CONFIRMED);
            order.setUpdatedAt(LocalDateTime.now());
            order = orderRepository.save(order);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Order completed successfully | requestId={} orderId={} status={} durationMs={}",
                    requestId, order.getId(), order.getStatus(), duration);

            return mapToResponse(order);

        } catch (ResourceAccessException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Order failed - service communication error | requestId={} error={} durationMs={}",
                    requestId, e.getMessage(), duration);
            throw new RuntimeException("Service unavailable: " + e.getMessage());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Order failed - unexpected error | requestId={} error={} durationMs={}",
                    requestId, e.getMessage(), duration);
            throw e;
        } finally {
            MDC.clear();
        }
    }

    private boolean checkInventory(String productId, int quantity, String requestId) {
        long start = System.currentTimeMillis();
        try {
            log.debug("Calling inventory service | requestId={} productId={} quantity={}",
                    requestId, productId, quantity);

            String url = inventoryServiceUrl + "/api/inventory/check?productId=" + productId + "&quantity=" + quantity;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            long duration = System.currentTimeMillis() - start;
            boolean available = Boolean.TRUE.equals(response.getBody().get("available"));

            log.info("Inventory check completed | requestId={} productId={} available={} responseTimeMs={}",
                    requestId, productId, available, duration);

            return available;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Inventory service call failed | requestId={} productId={} error={} responseTimeMs={}",
                    requestId, productId, e.getMessage(), duration);
            throw e;
        }
    }

    private boolean processPayment(Order order, String requestId) {
        long start = System.currentTimeMillis();
        try {
            log.debug("Calling payment service | requestId={} orderId={} amount={}",
                    requestId, order.getId(), order.getTotalAmount());

            OrderDtos.PaymentRequest paymentRequest = OrderDtos.PaymentRequest.builder()
                    .orderId(order.getId().toString())
                    .customerId(order.getCustomerId())
                    .amount(order.getTotalAmount())
                    .build();

            String url = paymentServiceUrl + "/api/payments/process";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, paymentRequest, Map.class);

            long duration = System.currentTimeMillis() - start;
            boolean success = Boolean.TRUE.equals(response.getBody().get("success"));

            log.info("Payment processing completed | requestId={} orderId={} success={} responseTimeMs={}",
                    requestId, order.getId(), success, duration);

            return success;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Payment service call failed | requestId={} orderId={} error={} responseTimeMs={}",
                    requestId, order.getId(), e.getMessage(), duration);
            throw e;
        }
    }

    public List<OrderDtos.OrderResponse> getAllOrders() {
        log.debug("Fetching all orders");
        return orderRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public OrderDtos.OrderResponse getOrderById(Long id) {
        log.debug("Fetching order | orderId={}", id);
        return orderRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> {
                    log.warn("Order not found | orderId={}", id);
                    return new RuntimeException("Order not found: " + id);
                });
    }

    private OrderDtos.OrderResponse mapToResponse(Order order) {
        return OrderDtos.OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .productId(order.getProductId())
                .quantity(order.getQuantity())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
