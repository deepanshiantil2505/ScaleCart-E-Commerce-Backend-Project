package com.scalecart.inventory.model;

import com.scalecart.product.model.Product;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventories", indexes = {
        @Index(name = "idx_inventory_product_id", columnList = "product_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "available_stock", nullable = false)
    private Integer availableStock;

    @Builder.Default
    @Column(name = "reserved_stock", nullable = false)
    private Integer reservedStock = 0;

    @Builder.Default
    @Column(name = "sold_stock", nullable = false)
    private Integer soldStock = 0;

    /**
     * Optimistic locking version column to detect concurrent modifications
     */
    @Version
    private Long version;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
