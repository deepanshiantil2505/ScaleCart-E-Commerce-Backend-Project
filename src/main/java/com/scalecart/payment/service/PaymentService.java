package com.scalecart.payment.service;

import com.scalecart.common.exceptions.InvalidOrderStateException;
import com.scalecart.common.exceptions.PaymentFailedException;
import com.scalecart.common.exceptions.ResourceNotFoundException;
import com.scalecart.event.model.PaymentProcessedEvent;
import com.scalecart.event.publisher.EventPublisher;
import com.scalecart.inventory.service.InventoryService;
import com.scalecart.order.model.Order;
import com.scalecart.order.model.OrderItem;
import com.scalecart.order.model.OrderStatus;
import com.scalecart.order.repository.OrderRepository;
import com.scalecart.order.service.OrderService;
import com.scalecart.payment.dto.PaymentRequest;
import com.scalecart.payment.dto.PaymentResponseDto;
import com.scalecart.payment.model.Payment;
import com.scalecart.payment.model.PaymentStatus;
import com.scalecart.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final EventPublisher eventPublisher;

    @Transactional
    public PaymentResponseDto processPayment(PaymentRequest request) {
        log.info("Processing payment for order ID: {}, amount: ${}", request.getOrderId(), request.getAmount());

        // 1. Idempotency Check: prevent duplicate charges on retry
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existingPayment.isPresent()) {
                log.info("Idempotent request detected. Returning existing payment: {}", existingPayment.get().getTransactionId());
                return mapToDto(existingPayment.get());
            }
        }

        // 2. Validate Order state
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + request.getOrderId()));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Order is not in PENDING state (Current state: " + order.getStatus() + "). Cannot process payment.");
        }

        if (order.getTotalAmount().compareTo(request.getAmount()) != 0) {
            throw new InvalidOrderStateException(String.format("Payment amount $%.2f does not match order total $%.2f",
                    request.getAmount(), order.getTotalAmount()));
        }

        String transactionId = "TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // 3. Mock Payment Processing: simulate success or simulated failure
        PaymentStatus status;
        String failureReason = null;

        if (request.isSimulateFailure()) {
            status = PaymentStatus.FAILED;
            failureReason = "Payment declined: Mock processor simulation - insufficient funds / card declined";
            log.warn("Payment failed simulation for order ID: {}", order.getId());
        } else {
            status = PaymentStatus.SUCCESS;
            log.info("Payment successful for order ID: {}, txn: {}", order.getId(), transactionId);
        }

        Payment payment = Payment.builder()
                .orderId(order.getId())
                .transactionId(transactionId)
                .amount(request.getAmount())
                .status(status)
                .paymentMethod(request.getPaymentMethod())
                .idempotencyKey(request.getIdempotencyKey())
                .failureReason(failureReason)
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        // 4. Publish Event to Kafka and local event bus
        eventPublisher.publishPaymentProcessed(PaymentProcessedEvent.builder()
                .paymentId(savedPayment.getId())
                .orderId(order.getId())
                .transactionId(savedPayment.getTransactionId())
                .amount(savedPayment.getAmount())
                .status(savedPayment.getStatus().name())
                .paymentMethod(savedPayment.getPaymentMethod().name())
                .build());

        // 5. Post-Payment State Transitions
        if (status == PaymentStatus.SUCCESS) {
            // Confirm Order
            orderService.updateOrderStatus(order.getId(), OrderStatus.CONFIRMED);

            // Confirm stock sale in inventory (moving from reserved to sold)
            for (OrderItem item : order.getItems()) {
                inventoryService.confirmStockSale(item.getProduct().getId(), item.getQuantity());
            }
        } else {
            throw new PaymentFailedException("Payment processing failed: " + failureReason);
        }

        return mapToDto(savedPayment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDto> getPaymentsForOrder(Long orderId) {
        return paymentRepository.findByOrderId(orderId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private PaymentResponseDto mapToDto(Payment payment) {
        return PaymentResponseDto.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .transactionId(payment.getTransactionId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
