package com.research.payment.service;

import com.research.payment.model.Payment;
import com.research.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentRepository paymentRepository;
    private final Random random = new Random();

    private final FailureInjectionService failureInjectionService;

    @Value("${payment.failure-rate:0.05}")
    private double failureRate;

    public Map<String, Object> processPayment(String orderId, String customerId, BigDecimal amount) {
        long start = System.currentTimeMillis();

        log.info("Payment processing started | orderId={} customerId={} amount={}",
                orderId, customerId, amount);

        try {
            failureInjectionService.applyGatewayTimeoutIfActive();
            failureInjectionService.applyHighLatencyIfActive();
            simulateGatewayDelay();

            boolean gatewaySuccess = random.nextDouble() > failureRate;
            long processingTime = System.currentTimeMillis() - start;

            Payment payment = Payment.builder()
                    .orderId(orderId)
                    .customerId(customerId)
                    .amount(amount)
                    .status(gatewaySuccess ? Payment.PaymentStatus.SUCCESS : Payment.PaymentStatus.FAILED)
                    .failureReason(gatewaySuccess ? null : "Payment gateway declined")
                    .createdAt(LocalDateTime.now())
                    .processingTimeMs(processingTime)
                    .build();

            paymentRepository.save(payment);

            if (gatewaySuccess) {
                log.info("Payment successful | orderId={} customerId={} amount={} paymentId={} processingTimeMs={}",
                        orderId, customerId, amount, payment.getId(), processingTime);
                return Map.of("success", true, "paymentId", payment.getId(), "processingTimeMs", processingTime);
            } else {
                log.error("Payment declined by gateway | orderId={} customerId={} amount={} processingTimeMs={}",
                        orderId, customerId, amount, processingTime);
                return Map.of("success", false, "reason", "Payment gateway declined", "processingTimeMs", processingTime);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long processingTime = System.currentTimeMillis() - start;
            log.error("Payment processing interrupted (gateway timeout) | orderId={} processingTimeMs={}",
                    orderId, processingTime);
            return Map.of(
                    "success",          false,
                    "reason",           "Payment gateway timeout",
                    "processingTimeMs", processingTime,
                    "failureScenario",  "GATEWAY_TIMEOUT"   // ← add this
            );

        } catch (RuntimeException e) {
            long processingTime = System.currentTimeMillis() - start;
            log.error("Payment processing exception | orderId={} customerId={} error={} processingTimeMs={}",
                    orderId, customerId, e.getMessage(), processingTime);
            throw e;
        }
    }

    private void simulateGatewayDelay() {
        try {
            // Simulate 50ms - 500ms gateway response time
            int delay = 50 + random.nextInt(450);
            log.debug("Simulating gateway delay | delayMs={}", delay);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void setFailureRate(double rate) {
        log.warn("Payment failure rate changed | previousRate={} newRate={}", this.failureRate, rate);
        this.failureRate = rate;
    }
}
