package com.scalecart.payment.repository;

import com.scalecart.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionId(String transactionId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByOrderId(Long orderId);
}
