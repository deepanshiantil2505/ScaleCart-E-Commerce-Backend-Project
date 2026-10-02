package com.scalecart.order.controller;

import com.scalecart.common.ApiResponse;
import com.scalecart.order.dto.CreateOrderRequest;
import com.scalecart.order.dto.OrderResponseDto;
import com.scalecart.order.model.OrderStatus;
import com.scalecart.order.service.OrderService;
import com.scalecart.user.model.Role;
import com.scalecart.user.model.User;
import com.scalecart.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Order Processing Service", description = "Endpoints for creating orders, calculating totals, tracking order status, and cancellations")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;

    @PostMapping
    @Operation(summary = "Place a new order", description = "Reserves inventory stock under concurrency row-locking and publishes OrderCreated event to Kafka")
    public ResponseEntity<ApiResponse<OrderResponseDto>> createOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponseDto order = orderService.createOrder(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed successfully", order));
    }

    @GetMapping
    @Operation(summary = "Get user order history or all orders (if Admin)")
    public ResponseEntity<ApiResponse<List<OrderResponseDto>>> getOrders(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getUserByEmail(userDetails.getUsername());
        List<OrderResponseDto> orders;
        if (user.getRole() == Role.ROLE_ADMIN) {
            orders = orderService.getAllOrders();
        } else {
            orders = orderService.getOrdersForUser(user.getEmail());
        }
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order details by ID")
    public ResponseEntity<ApiResponse<OrderResponseDto>> getOrderById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userService.getUserByEmail(userDetails.getUsername());
        OrderResponseDto order = orderService.getOrderById(id, user.getEmail(), user.getRole());
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order", description = "Cancels order and releases reserved inventory units back to available stock")
    public ResponseEntity<ApiResponse<OrderResponseDto>> cancelOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userService.getUserByEmail(userDetails.getUsername());
        OrderResponseDto order = orderService.cancelOrder(id, user.getEmail(), user.getRole());
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", order));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update order status (Admin Only)", description = "Transition status through PENDING, PAYMENT_COMPLETED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status
    ) {
        orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Order status updated to " + status, null));
    }
}
