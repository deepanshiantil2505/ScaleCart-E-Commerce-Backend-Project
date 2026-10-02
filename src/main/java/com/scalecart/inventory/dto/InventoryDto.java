package com.scalecart.inventory.dto;

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
public class InventoryDto implements Serializable {

    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private Integer availableStock;
    private Integer reservedStock;
    private Integer soldStock;
    private LocalDateTime updatedAt;
}
