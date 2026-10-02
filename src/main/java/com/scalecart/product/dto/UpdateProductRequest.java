package com.scalecart.product.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductRequest {

    private String name;
    private String description;

    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    private BigDecimal price;

    private Long categoryId;
    private String imageUrl;
    private Boolean active;
}
