package com.scalecart.product.service;

import com.scalecart.common.exceptions.DuplicateResourceException;
import com.scalecart.common.exceptions.ResourceNotFoundException;
import com.scalecart.inventory.model.Inventory;
import com.scalecart.inventory.repository.InventoryRepository;
import com.scalecart.inventory.service.InventoryService;
import com.scalecart.product.dto.CategoryDto;
import com.scalecart.product.dto.CreateProductRequest;
import com.scalecart.product.dto.ProductDto;
import com.scalecart.product.dto.UpdateProductRequest;
import com.scalecart.product.model.Category;
import com.scalecart.product.model.Product;
import com.scalecart.product.repository.CategoryRepository;
import com.scalecart.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "all_products", allEntries = true)
    })
    public ProductDto createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateResourceException("Product with SKU already exists: " + request.getSku());
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));

        Product product = Product.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .sku(request.getSku().trim().toUpperCase())
                .price(request.getPrice())
                .category(category)
                .imageUrl(request.getImageUrl())
                .active(true)
                .build();

        Product savedProduct = productRepository.save(product);

        // Initialize inventory stock
        inventoryService.initializeInventory(savedProduct, request.getInitialStock());
        log.info("Created new product ID: {} with SKU: {} and initial stock: {}",
                savedProduct.getId(), savedProduct.getSku(), request.getInitialStock());

        return mapToDto(savedProduct, request.getInitialStock());
    }

    /**
     * Demonstrates Redis Cache-Aside pattern:
     * - Returns cached product if found in Redis.
     * - If cache miss, fetches from PostgreSQL / H2 and caches for subsequent calls.
     */
    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public ProductDto getProductById(Long id) {
        log.info("Redis CACHE MISS - Fetching product ID: {} from primary database", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));

        Integer availableStock = inventoryRepository.findByProductId(id)
                .map(Inventory::getAvailableStock)
                .orElse(0);

        return mapToDto(product, availableStock);
    }

    @Cacheable(value = "all_products")
    @Transactional(readOnly = true)
    public List<ProductDto> getAllProducts() {
        log.info("Redis CACHE MISS - Fetching all active products from database");
        return productRepository.findByActiveTrue().stream()
                .map(p -> {
                    Integer stock = inventoryRepository.findByProductId(p.getId())
                            .map(Inventory::getAvailableStock)
                            .orElse(0);
                    return mapToDto(p, stock);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductDto> getProductsByCategory(Long categoryId) {
        return productRepository.findByCategoryIdAndActiveTrue(categoryId).stream()
                .map(p -> {
                    Integer stock = inventoryRepository.findByProductId(p.getId())
                            .map(Inventory::getAvailableStock)
                            .orElse(0);
                    return mapToDto(p, stock);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductDto> searchProducts(String query) {
        return productRepository.searchProducts(query).stream()
                .map(p -> {
                    Integer stock = inventoryRepository.findByProductId(p.getId())
                            .map(Inventory::getAvailableStock)
                            .orElse(0);
                    return mapToDto(p, stock);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products", key = "#id"),
            @CacheEvict(value = "all_products", allEntries = true)
    })
    public ProductDto updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));

        if (request.getName() != null) product.setName(request.getName().trim());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getPrice() != null) product.setPrice(request.getPrice());
        if (request.getImageUrl() != null) product.setImageUrl(request.getImageUrl());
        if (request.getActive() != null) product.setActive(request.getActive());

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));
            product.setCategory(category);
        }

        Product updated = productRepository.save(product);
        log.info("Updated product ID: {}, evicted Redis cache entries", id);

        Integer stock = inventoryRepository.findByProductId(id)
                .map(Inventory::getAvailableStock)
                .orElse(0);

        return mapToDto(updated, stock);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products", key = "#id"),
            @CacheEvict(value = "all_products", allEntries = true)
    })
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));

        product.setActive(false);
        productRepository.save(product);
        log.info("Soft-deleted product ID: {}, evicted Redis cache", id);
    }

    public ProductDto mapToDto(Product product, Integer availableStock) {
        return ProductDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .sku(product.getSku())
                .price(product.getPrice())
                .category(CategoryDto.builder()
                        .id(product.getCategory().getId())
                        .name(product.getCategory().getName())
                        .description(product.getCategory().getDescription())
                        .build())
                .imageUrl(product.getImageUrl())
                .active(product.isActive())
                .availableStock(availableStock)
                .createdAt(product.getCreatedAt())
                .build();
    }
}
