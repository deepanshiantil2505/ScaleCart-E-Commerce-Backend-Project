package com.scalecart.payment.controller;

import com.scalecart.common.ApiResponse;
import com.scalecart.payment.dto.PaymentRequest;
import com.scalecart.payment.dto.PaymentResponseDto;
import com.scalecart.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Service", description = "Endpoints for processing payments, idempotency handling, and simulating gateway responses")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/process")
    @Operation(summary = "Process a payment for an order", description = "Mock payment gateway with idempotency key support and Kafka event emission")
    public ResponseEntity<ApiResponse<PaymentResponseDto>> processPayment(@Valid @RequestBody PaymentRequest request) {
        PaymentResponseDto payment = paymentService.processPayment(request);
        return ResponseEntity.ok(ApiResponse.success("Payment processed successfully", payment));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get payment transaction history for an order")
    public ResponseEntity<ApiResponse<List<PaymentResponseDto>>> getPaymentsByOrderId(@PathVariable Long orderId) {
        List<PaymentResponseDto> payments = paymentService.getPaymentsForOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success(payments));
    }
}
