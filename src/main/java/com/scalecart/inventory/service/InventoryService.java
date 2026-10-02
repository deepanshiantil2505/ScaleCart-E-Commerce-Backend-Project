package com.scalecart.inventory.service;

import com.scalecart.common.exceptions.InsufficientStockException;
import com.scalecart.common.exceptions.ResourceNotFoundException;
import com.scalecart.event.model.InventoryUpdatedEvent;
import com.scalecart.event.publisher.EventPublisher;
import com.scalecart.inventory.dto.InventoryDto;
import com.scalecart.inventory.model.Inventory;
import com.scalecart.inventory.repository.InventoryRepository;
import com.scalecart.product.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final EventPublisher eventPublisher;

    /**
     * Acquires a database-level PESSIMISTIC_WRITE lock ('SELECT ... FOR UPDATE') on the inventory record.
     * Prevents race conditions and overselling when multiple concurrent users attempt to buy the last available item.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(value = "products", allEntries = true)
    public InventoryDto reserveStock(Long productId, Integer quantity) {
        log.info("Attempting to reserve {} units for product ID: {}", quantity, productId);

        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product ID: " + productId));

        if (inventory.getAvailableStock() < quantity) {
            log.warn("Stock shortage for product ID {}: available={}, requested={}",
                    productId, inventory.getAvailableStock(), quantity);
            throw new InsufficientStockException(
                    String.format("Insufficient stock for product ID: %d. Available: %d, Requested: %d",
                            productId, inventory.getAvailableStock(), quantity));
        }

        inventory.setAvailableStock(inventory.getAvailableStock() - quantity);
        inventory.setReservedStock(inventory.getReservedStock() + quantity);

        Inventory saved = inventoryRepository.save(inventory);
        log.info("Reserved {} units for product ID {}. Remaining available: {}",
                quantity, productId, saved.getAvailableStock());

        eventPublisher.publishInventoryUpdated(InventoryUpdatedEvent.builder()
                .productId(productId)
                .availableStock(saved.getAvailableStock())
                .reservedStock(saved.getReservedStock())
                .soldStock(saved.getSoldStock())
                .operationType("RESERVED")
                .build());

        return mapToDto(saved);
    }

    /**
     * Confirms the sale once payment is successful: moves stock from reserved to sold.
     */
    @Transactional
    public InventoryDto confirmStockSale(Long productId, Integer quantity) {
        log.info("Confirming sale of {} units for product ID: {}", quantity, productId);

        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product ID: " + productId));

        int newReserved = Math.max(0, inventory.getReservedStock() - quantity);
        inventory.setReservedStock(newReserved);
        inventory.setSoldStock(inventory.getSoldStock() + quantity);

        Inventory saved = inventoryRepository.save(inventory);

        eventPublisher.publishInventoryUpdated(InventoryUpdatedEvent.builder()
                .productId(productId)
                .availableStock(saved.getAvailableStock())
                .reservedStock(saved.getReservedStock())
                .soldStock(saved.getSoldStock())
                .operationType("CONFIRMED")
                .build());

        return mapToDto(saved);
    }

    /**
     * Releases reserved stock back to available stock (e.g. upon order cancellation or payment failure).
     */
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public InventoryDto releaseStock(Long productId, Integer quantity) {
        log.info("Releasing {} reserved units for product ID: {}", quantity, productId);

        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product ID: " + productId));

        int newReserved = Math.max(0, inventory.getReservedStock() - quantity);
        inventory.setReservedStock(newReserved);
        inventory.setAvailableStock(inventory.getAvailableStock() + quantity);

        Inventory saved = inventoryRepository.save(inventory);

        eventPublisher.publishInventoryUpdated(InventoryUpdatedEvent.builder()
                .productId(productId)
                .availableStock(saved.getAvailableStock())
                .reservedStock(saved.getReservedStock())
                .soldStock(saved.getSoldStock())
                .operationType("RELEASED")
                .build());

        return mapToDto(saved);
    }

    /**
     * Replenishes stock for a product (Admin operation).
     */
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public InventoryDto replenishStock(Long productId, Integer quantity) {
        log.info("Replenishing {} units for product ID: {}", quantity, productId);

        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product ID: " + productId));

        inventory.setAvailableStock(inventory.getAvailableStock() + quantity);
        Inventory saved = inventoryRepository.save(inventory);

        eventPublisher.publishInventoryUpdated(InventoryUpdatedEvent.builder()
                .productId(productId)
                .availableStock(saved.getAvailableStock())
                .reservedStock(saved.getReservedStock())
                .soldStock(saved.getSoldStock())
                .operationType("REPLENISHED")
                .build());

        return mapToDto(saved);
    }

    @Transactional
    public Inventory initializeInventory(Product product, Integer initialStock) {
        Inventory inventory = Inventory.builder()
                .product(product)
                .availableStock(initialStock != null ? initialStock : 0)
                .reservedStock(0)
                .soldStock(0)
                .build();

        return inventoryRepository.save(inventory);
    }

    @Transactional(readOnly = true)
    public InventoryDto getInventoryByProductId(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product ID: " + productId));
        return mapToDto(inventory);
    }

    private InventoryDto mapToDto(Inventory inventory) {
        return InventoryDto.builder()
                .id(inventory.getId())
                .productId(inventory.getProduct().getId())
                .productName(inventory.getProduct().getName())
                .productSku(inventory.getProduct().getSku())
                .availableStock(inventory.getAvailableStock())
                .reservedStock(inventory.getReservedStock())
                .soldStock(inventory.getSoldStock())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }
}
