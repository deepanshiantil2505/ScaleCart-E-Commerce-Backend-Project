package com.scalecart.inventory.controller;

import com.scalecart.common.ApiResponse;
import com.scalecart.inventory.dto.InventoryDto;
import com.scalecart.inventory.dto.StockReplenishRequest;
import com.scalecart.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory Service", description = "Endpoints for managing product stock, concurrency locking, and replenishment")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/product/{productId}")
    @Operation(summary = "Get stock levels for a product", description = "Displays available, reserved, and sold stock units")
    public ResponseEntity<ApiResponse<InventoryDto>> getInventory(@PathVariable Long productId) {
        InventoryDto inventory = inventoryService.getInventoryByProductId(productId);
        return ResponseEntity.ok(ApiResponse.success(inventory));
    }

    @PostMapping("/replenish")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replenish product stock (Admin Only)", description = "Adds new stock quantity to a product's available inventory")
    public ResponseEntity<ApiResponse<InventoryDto>> replenishStock(@Valid @RequestBody StockReplenishRequest request) {
        InventoryDto inventory = inventoryService.replenishStock(request.getProductId(), request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success("Stock replenished successfully", inventory));
    }
}
