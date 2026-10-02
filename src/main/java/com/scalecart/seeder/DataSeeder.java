package com.scalecart.seeder;

import com.scalecart.inventory.service.InventoryService;
import com.scalecart.product.model.Category;
import com.scalecart.product.model.Product;
import com.scalecart.product.repository.CategoryRepository;
import com.scalecart.product.repository.ProductRepository;
import com.scalecart.user.model.Role;
import com.scalecart.user.model.User;
import com.scalecart.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers();
        seedCatalog();
    }

    private void seedUsers() {
        if (userRepository.count() == 0) {
            log.info("Seeding default users...");

            User admin = User.builder()
                    .email("admin@scalecart.com")
                    .password(passwordEncoder.encode("Admin@123"))
                    .firstName("ScaleCart")
                    .lastName("Admin")
                    .role(Role.ROLE_ADMIN)
                    .enabled(true)
                    .build();
            userRepository.save(admin);

            User customer = User.builder()
                    .email("customer@scalecart.com")
                    .password(passwordEncoder.encode("Customer@123"))
                    .firstName("John")
                    .lastName("Doe")
                    .role(Role.ROLE_CUSTOMER)
                    .enabled(true)
                    .build();
            userRepository.save(customer);

            log.info("Default users seeded: admin@scalecart.com (Admin@123) and customer@scalecart.com (Customer@123)");
        }
    }

    private void seedCatalog() {
        if (categoryRepository.count() == 0) {
            log.info("Seeding categories and catalog products with inventory...");

            Category electronics = categoryRepository.save(Category.builder()
                    .name("Electronics")
                    .description("Laptops, Audio, Mobile Phones, and Computer Hardware")
                    .build());

            Category books = categoryRepository.save(Category.builder()
                    .name("Books")
                    .description("Software Architecture, Engineering, and Science Books")
                    .build());

            Category fashion = categoryRepository.save(Category.builder()
                    .name("Apparel & Fashion")
                    .description("Outdoor jackets, shirts, and casual wear")
                    .build());

            createProductWithStock("Apple MacBook Pro 16\" M3 Max",
                    "Apple M3 Max chip with 16-core CPU and 40-core GPU, 36GB Unified Memory, 1TB SSD Storage.",
                    "MBP-M3-16", new BigDecimal("3499.00"), electronics,
                    "https://images.unsplash.com/photo-1517336714731-489689fd1ca8", 15);

            createProductWithStock("Sony WH-1000XM5 Wireless Headphones",
                    "Industry-leading noise canceling headphones with Auto NC Optimizer, crystal-clear hands-free calling.",
                    "SONY-WH1000XM5", new BigDecimal("399.99"), electronics,
                    "https://images.unsplash.com/photo-1505740420928-5e560c06d30e", 30);

            createProductWithStock("Designing Data-Intensive Applications",
                    "The Definitive Guide to Distributed Systems and Big Data by Martin Kleppmann.",
                    "BOOK-DDIA-01", new BigDecimal("49.99"), books,
                    "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c", 50);

            createProductWithStock("Patagonia Better Sweater Fleece Jacket",
                    "Warm, polyester knitted fleece jacket with a low-impact dyeing process.",
                    "PAT-FLEECE-M", new BigDecimal("139.00"), fashion,
                    "https://images.unsplash.com/photo-1551028719-00167b16eac5", 25);

            createProductWithStock("Keychron Q1 Pro Mechanical Keyboard",
                    "Custom Wireless Mechanical Keyboard with QMK/VIA support, CNC Aluminum body, hot-swappable switches.",
                    "KEY-Q1-PRO", new BigDecimal("199.00"), electronics,
                    "https://images.unsplash.com/photo-1587829741301-dc798b83add3", 20);

            log.info("Catalog products and inventory stocks seeded successfully.");
        }
    }

    private void createProductWithStock(String name, String description, String sku,
                                       BigDecimal price, Category category, String imageUrl, int initialStock) {
        Product product = Product.builder()
                .name(name)
                .description(description)
                .sku(sku)
                .price(price)
                .category(category)
                .imageUrl(imageUrl)
                .active(true)
                .build();

        Product savedProduct = productRepository.save(product);
        inventoryService.initializeInventory(savedProduct, initialStock);
    }
}
