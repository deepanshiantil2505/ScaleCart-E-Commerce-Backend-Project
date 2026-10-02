package com.scalecart.product;

import com.scalecart.common.exceptions.ResourceNotFoundException;
import com.scalecart.inventory.model.Inventory;
import com.scalecart.inventory.repository.InventoryRepository;
import com.scalecart.inventory.service.InventoryService;
import com.scalecart.product.dto.CreateProductRequest;
import com.scalecart.product.dto.ProductDto;
import com.scalecart.product.dto.UpdateProductRequest;
import com.scalecart.product.model.Category;
import com.scalecart.product.model.Product;
import com.scalecart.product.repository.CategoryRepository;
import com.scalecart.product.repository.ProductRepository;
import com.scalecart.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private ProductService productService;

    private Category electronicsCategory;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        electronicsCategory = Category.builder()
                .id(1L)
                .name("Electronics")
                .description("Electronic devices")
                .build();

        sampleProduct = Product.builder()
                .id(101L)
                .name("Test Laptop")
                .description("A powerful testing laptop")
                .sku("LAP-TEST-01")
                .price(new BigDecimal("1200.00"))
                .category(electronicsCategory)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Should create product and initialize inventory stock")
    void testCreateProduct() {
        CreateProductRequest request = CreateProductRequest.builder()
                .name("Test Laptop")
                .description("A powerful testing laptop")
                .sku("LAP-TEST-01")
                .price(new BigDecimal("1200.00"))
                .categoryId(1L)
                .initialStock(25)
                .build();

        when(productRepository.existsBySku("LAP-TEST-01")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronicsCategory));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        ProductDto result = productService.createProduct(request);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Laptop");
        assertThat(result.getSku()).isEqualTo("LAP-TEST-01");
        assertThat(result.getAvailableStock()).isEqualTo(25);

        verify(inventoryService, times(1)).initializeInventory(sampleProduct, 25);
    }

    @Test
    @DisplayName("Should retrieve product by ID and merge with inventory stock")
    void testGetProductById() {
        Inventory sampleInventory = Inventory.builder()
                .id(1L)
                .product(sampleProduct)
                .availableStock(18)
                .build();

        when(productRepository.findById(101L)).thenReturn(Optional.of(sampleProduct));
        when(inventoryRepository.findByProductId(101L)).thenReturn(Optional.of(sampleInventory));

        ProductDto result = productService.getProductById(101L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(101L);
        assertThat(result.getAvailableStock()).isEqualTo(18);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for non-existent product ID")
    void testGetProductNotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    @DisplayName("Should update product fields properly")
    void testUpdateProduct() {
        UpdateProductRequest updateReq = UpdateProductRequest.builder()
                .name("Updated Laptop Name")
                .price(new BigDecimal("1299.99"))
                .build();

        when(productRepository.findById(101L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);
        when(inventoryRepository.findByProductId(101L)).thenReturn(Optional.empty());

        ProductDto result = productService.updateProduct(101L, updateReq);

        assertThat(result).isNotNull();
        verify(productRepository, times(1)).save(sampleProduct);
    }
}
