package com.scalecart.order;

import com.scalecart.event.model.OrderCreatedEvent;
import com.scalecart.event.publisher.EventPublisher;
import com.scalecart.inventory.service.InventoryService;
import com.scalecart.order.dto.CreateOrderRequest;
import com.scalecart.order.dto.OrderItemRequest;
import com.scalecart.order.dto.OrderResponseDto;
import com.scalecart.order.model.Order;
import com.scalecart.order.model.OrderItem;
import com.scalecart.order.model.OrderStatus;
import com.scalecart.order.repository.OrderRepository;
import com.scalecart.order.service.OrderService;
import com.scalecart.product.model.Category;
import com.scalecart.product.model.Product;
import com.scalecart.product.repository.ProductRepository;
import com.scalecart.user.model.Role;
import com.scalecart.user.model.User;
import com.scalecart.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    private User sampleUser;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .email("shopper@example.com")
                .firstName("Shopper")
                .lastName("One")
                .role(Role.ROLE_CUSTOMER)
                .build();

        Category category = Category.builder().id(1L).name("Books").build();

        sampleProduct = Product.builder()
                .id(200L)
                .name("Clean Architecture Book")
                .sku("BOOK-CA-01")
                .price(new BigDecimal("50.00"))
                .category(category)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Should successfully place an order, reserve stock, and emit OrderCreated event")
    void testCreateOrder() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingAddress("123 Tech Blvd, Silicon Valley")
                .items(List.of(
                        OrderItemRequest.builder().productId(200L).quantity(2).build()
                ))
                .build();

        when(userRepository.findByEmail("shopper@example.com")).thenReturn(Optional.of(sampleUser));
        when(productRepository.findById(200L)).thenReturn(Optional.of(sampleProduct));

        Order savedOrder = Order.builder()
                .id(501L)
                .orderNumber("ORD-12345")
                .user(sampleUser)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("100.00"))
                .shippingAddress("123 Tech Blvd, Silicon Valley")
                .items(new ArrayList<>())
                .build();

        OrderItem item = OrderItem.builder()
                .id(1L)
                .order(savedOrder)
                .product(sampleProduct)
                .quantity(2)
                .unitPrice(new BigDecimal("50.00"))
                .subtotal(new BigDecimal("100.00"))
                .build();
        savedOrder.addItem(item);

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderResponseDto response = orderService.createOrder("shopper@example.com", request);

        assertThat(response).isNotNull();
        assertThat(response.getOrderNumber()).isEqualTo("ORD-12345");
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);

        verify(inventoryService, times(1)).reserveStock(200L, 2);
        verify(eventPublisher, times(1)).publishOrderCreated(any(OrderCreatedEvent.class));
    }

    @Test
    @DisplayName("Should cancel order and release reserved stock")
    void testCancelOrder() {
        Order pendingOrder = Order.builder()
                .id(501L)
                .orderNumber("ORD-12345")
                .user(sampleUser)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("100.00"))
                .shippingAddress("123 Tech Blvd")
                .items(new ArrayList<>())
                .build();

        OrderItem item = OrderItem.builder()
                .id(1L)
                .order(pendingOrder)
                .product(sampleProduct)
                .quantity(2)
                .unitPrice(new BigDecimal("50.00"))
                .subtotal(new BigDecimal("100.00"))
                .build();
        pendingOrder.addItem(item);

        when(orderRepository.findById(501L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(pendingOrder);

        OrderResponseDto result = orderService.cancelOrder(501L, "shopper@example.com", Role.ROLE_CUSTOMER);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryService, times(1)).releaseStock(200L, 2);
    }
}
