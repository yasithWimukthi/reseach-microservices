package com.research.payment.repository;

import com.research.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderId(String orderId);
    List<Payment> findByCustomerId(String customerId);
    List<Payment> findByStatus(Payment.PaymentStatus status);
}
