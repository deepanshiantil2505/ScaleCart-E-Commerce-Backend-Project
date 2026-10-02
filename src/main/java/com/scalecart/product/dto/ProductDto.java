package com.scalecart.product.dto;

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
public class ProductDto implements Serializable {

    private Long id;
    private String name;
    private String description;
    private String sku;
    private BigDecimal price;
    private CategoryDto category;
    private String imageUrl;
    private boolean active;
    private Integer availableStock;
    private LocalDateTime createdAt;
}
