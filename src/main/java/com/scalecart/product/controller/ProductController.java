package com.scalecart.product.controller;

import com.scalecart.common.ApiResponse;
import com.scalecart.product.dto.CreateProductRequest;
import com.scalecart.product.dto.ProductDto;
import com.scalecart.product.dto.UpdateProductRequest;
import com.scalecart.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Product Catalog Service", description = "Endpoints for browsing products, searching, and managing product catalog with Redis caching")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Browse all active products", description = "Results are cached in Redis to achieve low-latency response times")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getAllProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search
    ) {
        List<ProductDto> products;
        if (search != null && !search.isBlank()) {
            products = productService.searchProducts(search.trim());
        } else if (categoryId != null) {
            products = productService.getProductsByCategory(categoryId);
        } else {
            products = productService.getAllProducts();
        }
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Demonstrates Redis Cache-Aside pattern (Redis -> DB fallback -> Redis populating)")
    public ResponseEntity<ApiResponse<ProductDto>> getProductById(@PathVariable Long id) {
        ProductDto product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a new product (Admin Only)", description = "Creates product, initializes inventory, and invalidates Redis cache")
    public ResponseEntity<ApiResponse<ProductDto>> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductDto product = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", product));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update an existing product (Admin Only)", description = "Updates details and evicts Redis cache")
    public ResponseEntity<ApiResponse<ProductDto>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        ProductDto updated = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete / Deactivate product (Admin Only)")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
    }
}
