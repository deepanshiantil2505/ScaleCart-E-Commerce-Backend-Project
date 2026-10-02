package com.scalecart.order.service;

import com.scalecart.common.exceptions.InvalidOrderStateException;
import com.scalecart.common.exceptions.ResourceNotFoundException;
import com.scalecart.event.model.OrderCreatedEvent;
import com.scalecart.event.publisher.EventPublisher;
import com.scalecart.inventory.service.InventoryService;
import com.scalecart.order.dto.CreateOrderRequest;
import com.scalecart.order.dto.OrderItemDto;
import com.scalecart.order.dto.OrderItemRequest;
import com.scalecart.order.dto.OrderResponseDto;
import com.scalecart.order.model.Order;
import com.scalecart.order.model.OrderItem;
import com.scalecart.order.model.OrderStatus;
import com.scalecart.order.repository.OrderRepository;
import com.scalecart.product.model.Product;
import com.scalecart.product.repository.ProductRepository;
import com.scalecart.user.model.Role;
import com.scalecart.user.model.User;
import com.scalecart.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final EventPublisher eventPublisher;

    /**
     * Creates an order with concurrency-safe stock reservation and publishes Kafka event.
     */
    @Transactional
    public OrderResponseDto createOrder(String userEmail, CreateOrderRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        String orderNumber = "ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .user(user)
                .status(OrderStatus.PENDING)
                .shippingAddress(request.getShippingAddress().trim())
                .totalAmount(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .build();

        BigDecimal runningTotal = BigDecimal.ZERO;
        List<OrderCreatedEvent.OrderItemEventPayload> eventPayloads = new ArrayList<>();

        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + itemReq.getProductId()));

            if (!product.isActive()) {
                throw new InvalidOrderStateException("Product '" + product.getName() + "' is currently inactive.");
            }

            // Reserve inventory stock using PESSIMISTIC_WRITE row lock to guarantee zero overselling
            inventoryService.reserveStock(product.getId(), itemReq.getQuantity());

            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            runningTotal = runningTotal.add(subtotal);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(subtotal)
                    .build();

            order.addItem(orderItem);

            eventPayloads.add(OrderCreatedEvent.OrderItemEventPayload.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(product.getPrice())
                    .build());
        }

        order.setTotalAmount(runningTotal);
        Order savedOrder = orderRepository.save(order);
        log.info("Created order ID: {} with number: {}, total: ${}", savedOrder.getId(), savedOrder.getOrderNumber(), runningTotal);

        // Publish OrderCreated event to Kafka
        eventPublisher.publishOrderCreated(OrderCreatedEvent.builder()
                .orderId(savedOrder.getId())
                .orderNumber(savedOrder.getOrderNumber())
                .userEmail(user.getEmail())
                .totalAmount(savedOrder.getTotalAmount())
                .items(eventPayloads)
                .build());

        return mapToDto(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponseDto getOrderById(Long orderId, String currentUserEmail, Role role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        if (role != Role.ROLE_ADMIN && !order.getUser().getEmail().equalsIgnoreCase(currentUserEmail)) {
            throw new AccessDeniedException("You are not authorized to view this order.");
        }

        return mapToDto(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDto> getOrdersForUser(String userEmail) {
        return orderRepository.findByUserEmailOrderByCreatedAtDesc(userEmail).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDto> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrderResponseDto cancelOrder(Long orderId, String currentUserEmail, Role role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        if (role != Role.ROLE_ADMIN && !order.getUser().getEmail().equalsIgnoreCase(currentUserEmail)) {
            throw new AccessDeniedException("You are not authorized to cancel this order.");
        }

        if (order.getStatus() == OrderStatus.CANCELLED ||
                order.getStatus() == OrderStatus.SHIPPED ||
                order.getStatus() == OrderStatus.DELIVERED) {
            throw new InvalidOrderStateException("Cannot cancel an order in " + order.getStatus() + " status.");
        }

        // Release reserved stock back to available stock
        for (OrderItem item : order.getItems()) {
            inventoryService.releaseStock(item.getProduct().getId(), item.getQuantity());
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order updated = orderRepository.save(order);
        log.info("Cancelled order: {}, stock released back to available inventory", updated.getOrderNumber());

        return mapToDto(updated);
    }

    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));
        order.setStatus(newStatus);
        orderRepository.save(order);
        log.info("Updated order ID: {} status to: {}", orderId, newStatus);
    }

    public Order getOrderEntity(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));
    }

    private OrderResponseDto mapToDto(Order order) {
        List<OrderItemDto> itemDtos = order.getItems().stream()
                .map(item -> OrderItemDto.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .productSku(item.getProduct().getSku())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return OrderResponseDto.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerEmail(order.getUser().getEmail())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .shippingAddress(order.getShippingAddress())
                .items(itemDtos)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
