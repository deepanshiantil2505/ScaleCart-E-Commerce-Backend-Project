package com.scalecart.payment;

import com.scalecart.common.exceptions.PaymentFailedException;
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
import com.scalecart.payment.model.PaymentMethod;
import com.scalecart.payment.model.PaymentStatus;
import com.scalecart.payment.repository.PaymentRepository;
import com.scalecart.payment.service.PaymentService;
import com.scalecart.product.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        Product product = Product.builder().id(10L).name("Gadget").price(new BigDecimal("200.00")).build();
        pendingOrder = Order.builder()
                .id(100L)
                .orderNumber("ORD-100")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("200.00"))
                .items(new ArrayList<>())
                .build();

        OrderItem item = OrderItem.builder().id(1L).order(pendingOrder).product(product).quantity(1).build();
        pendingOrder.addItem(item);
    }

    @Test
    @DisplayName("Should successfully process payment, update order to CONFIRMED, and confirm stock sale")
    void testProcessPaymentSuccess() {
        PaymentRequest request = PaymentRequest.builder()
                .orderId(100L)
                .amount(new BigDecimal("200.00"))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .idempotencyKey("KEY-12345")
                .simulateFailure(false)
                .build();

        Payment savedPayment = Payment.builder()
                .id(1L)
                .orderId(100L)
                .transactionId("TXN-123")
                .amount(new BigDecimal("200.00"))
                .status(PaymentStatus.SUCCESS)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .idempotencyKey("KEY-12345")
                .build();

        when(paymentRepository.findByIdempotencyKey("KEY-12345")).thenReturn(Optional.empty());
        when(orderRepository.findById(100L)).thenReturn(Optional.of(pendingOrder));
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        PaymentResponseDto response = paymentService.processPayment(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getTransactionId()).isEqualTo("TXN-123");

        verify(orderService, times(1)).updateOrderStatus(100L, OrderStatus.CONFIRMED);
        verify(inventoryService, times(1)).confirmStockSale(10L, 1);
        verify(eventPublisher, times(1)).publishPaymentProcessed(any(PaymentProcessedEvent.class));
    }

    @Test
    @DisplayName("Should return existing payment when idempotency key is repeated (Zero Double-Charging)")
    void testIdempotentPayment() {
        Payment existingPayment = Payment.builder()
                .id(99L)
                .orderId(100L)
                .transactionId("TXN-EXISTING")
                .amount(new BigDecimal("200.00"))
                .status(PaymentStatus.SUCCESS)
                .paymentMethod(PaymentMethod.UPI)
                .idempotencyKey("RETRY-KEY-999")
                .build();

        PaymentRequest request = PaymentRequest.builder()
                .orderId(100L)
                .amount(new BigDecimal("200.00"))
                .paymentMethod(PaymentMethod.UPI)
                .idempotencyKey("RETRY-KEY-999")
                .build();

        when(paymentRepository.findByIdempotencyKey("RETRY-KEY-999")).thenReturn(Optional.of(existingPayment));

        PaymentResponseDto response = paymentService.processPayment(request);

        assertThat(response).isNotNull();
        assertThat(response.getTransactionId()).isEqualTo("TXN-EXISTING");
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(orderService, never()).updateOrderStatus(any(), any());
    }

    @Test
    @DisplayName("Should throw PaymentFailedException when simulation failure is requested")
    void testSimulatedPaymentFailure() {
        PaymentRequest request = PaymentRequest.builder()
                .orderId(100L)
                .amount(new BigDecimal("200.00"))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .simulateFailure(true)
                .build();

        Payment failedPayment = Payment.builder()
                .id(2L)
                .orderId(100L)
                .transactionId("TXN-FAILED")
                .amount(new BigDecimal("200.00"))
                .status(PaymentStatus.FAILED)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .failureReason("Payment declined: Mock processor simulation")
                .build();

        when(orderRepository.findById(100L)).thenReturn(Optional.of(pendingOrder));
        when(paymentRepository.save(any(Payment.class))).thenReturn(failedPayment);

        assertThatThrownBy(() -> paymentService.processPayment(request))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("Payment processing failed");

        verify(orderService, never()).updateOrderStatus(any(), any());
    }
}
