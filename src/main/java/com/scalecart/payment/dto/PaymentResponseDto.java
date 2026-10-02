package com.scalecart.payment.dto;

import com.scalecart.payment.model.PaymentMethod;
import com.scalecart.payment.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponseDto implements Serializable {

    private Long id;
    private Long orderId;
    private String transactionId;
    private BigDecimal amount;
    private PaymentStatus status;
    private PaymentMethod paymentMethod;
    private String failureReason;
    private LocalDateTime createdAt;
}
