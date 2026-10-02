package com.scalecart.inventory;

import com.scalecart.common.exceptions.InsufficientStockException;
import com.scalecart.inventory.model.Inventory;
import com.scalecart.inventory.repository.InventoryRepository;
import com.scalecart.inventory.service.InventoryService;
import com.scalecart.product.model.Category;
import com.scalecart.product.model.Product;
import com.scalecart.product.repository.CategoryRepository;
import com.scalecart.product.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class InventoryConcurrencyTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("Concurrency Simulation: Two concurrent users attempting to buy the LAST available item (Stock=1). Exactly 1 succeeds, 1 fails.")
    void testConcurrentStockReservationPessimisticLock() throws InterruptedException {
        // Arrange: Category, Product, and Inventory with only 1 item in stock
        Category category = categoryRepository.save(Category.builder()
                .name("Limited Edition")
                .description("Rare Items")
                .build());

        Product product = productRepository.save(Product.builder()
                .name("Exclusive Collector Item")
                .description("Only 1 available in inventory")
                .sku("RARE-ITEM-001")
                .price(new BigDecimal("999.99"))
                .category(category)
                .active(true)
                .build());

        Inventory initialInventory = inventoryRepository.save(Inventory.builder()
                .product(product)
                .availableStock(1)
                .reservedStock(0)
                .soldStock(0)
                .build());

        Long productId = product.getId();

        // Act: 2 concurrent threads trying to reserve 1 unit simultaneously
        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Guarantee both threads hit the method simultaneously
                    inventoryService.reserveStock(productId, 1);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException ex) {
                    failureCount.incrementAndGet();
                } catch (Exception ex) {
                    // unexpected error
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown(); // Fire both threads at the exact same instant
        doneLatch.await();
        executor.shutdown();

        // Assert:
        // Pessimistic Locking ensures strict serialization of stock reservation
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        Inventory finalInventory = inventoryRepository.findByProductId(productId).orElseThrow();
        assertThat(finalInventory.getAvailableStock()).isEqualTo(0);
        assertThat(finalInventory.getReservedStock()).isEqualTo(1);
        assertThat(finalInventory.getSoldStock()).isEqualTo(0);
    }
}
