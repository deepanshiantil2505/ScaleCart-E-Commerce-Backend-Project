package com.scalecart.event.model;

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
public class PaymentProcessedEvent implements Serializable {

    private Long paymentId;
    private Long orderId;
    private String transactionId;
    private BigDecimal amount;
    private String status; // SUCCESS or FAILED
    private String paymentMethod;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
