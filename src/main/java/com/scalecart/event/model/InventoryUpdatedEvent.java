package com.scalecart.event.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryUpdatedEvent implements Serializable {

    private Long productId;
    private Integer availableStock;
    private Integer reservedStock;
    private Integer soldStock;
    private String operationType; // RESERVED, CONFIRMED, RELEASED, REPLENISHED
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
