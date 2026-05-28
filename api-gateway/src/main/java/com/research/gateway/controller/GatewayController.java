package com.research.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);

    private final RestTemplate restTemplate;

    @Value("${services.order.url}")
    private String orderServiceUrl;

    @Value("${services.inventory.url}")
    private String inventoryServiceUrl;

    @Value("${services.payment.url}")
    private String paymentServiceUrl;

    @RequestMapping("/orders/**")
    public ResponseEntity<?> proxyOrders(HttpServletRequest request, @RequestBody(required = false) Object body) {
        return proxy(request, body, orderServiceUrl, "order-service");
    }

    @RequestMapping("/inventory/**")
    public ResponseEntity<?> proxyInventory(HttpServletRequest request, @RequestBody(required = false) Object body) {
        return proxy(request, body, inventoryServiceUrl, "inventory-service");
    }

    @RequestMapping("/payments/**")
    public ResponseEntity<?> proxyPayments(HttpServletRequest request, @RequestBody(required = false) Object body) {
        return proxy(request, body, paymentServiceUrl, "payment-service");
    }

    private ResponseEntity<?> proxy(HttpServletRequest request, Object body, String targetUrl, String serviceName) {
        String requestId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        String path = request.getRequestURI();
        String method = request.getMethod();

        log.info("Gateway routing | requestId={} method={} path={} target={}",
                requestId, method, path, serviceName);

        try {
            String url = targetUrl + "/api" + path;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Request-ID", requestId);

            HttpEntity<Object> entity = new HttpEntity<>(body, headers);
            HttpMethod httpMethod = HttpMethod.valueOf(method);

            ResponseEntity<Object> response = restTemplate.exchange(url, httpMethod, entity, Object.class);

            long duration = System.currentTimeMillis() - start;
            log.info("Gateway response | requestId={} path={} status={} durationMs={}",
                    requestId, path, response.getStatusCode().value(), duration);

            return response;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Gateway routing failed | requestId={} path={} target={} error={} durationMs={}",
                    requestId, path, serviceName, e.getMessage(), duration);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Service unavailable: " + serviceName);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("API Gateway is running");
    }
}
