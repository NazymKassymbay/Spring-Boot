package com.example.shop.product.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root of the product context (lecture weeks 6-7, slides 18 and 21).
 * Outside code changes a product only through these methods, so these rules always hold:
 * <ul>
 *   <li>the price is above zero;</li>
 *   <li>the stock never goes below zero;</li>
 *   <li>sku and name are never blank.</li>
 * </ul>
 * There are getters for reading and no setters. This class knows nothing about JPA:
 * the table mapping is ProductEntity in product.infra.
 */
public final class Product {

    private final Long id;                 // null until the first save
    private String sku;
    private String name;
    private String description;
    private BigDecimal price;
    private int stockQuantity;
    private CategoryRef category;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;            // optimistic locking, carried through the mapper

    private Product(Long id, String sku, String name, String description, BigDecimal price,
                    int stockQuantity, CategoryRef category, Instant createdAt, Instant updatedAt,
                    long version) {
        this.id = id;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.version = version;
        apply(sku, name, description, price, stockQuantity, category, updatedAt);
    }

    // Factory for a new product
    public static Product create(String sku, String name, String description, BigDecimal price,
                                 int stockQuantity, CategoryRef category, Instant now) {
        return new Product(null, sku, name, description, price, stockQuantity, category, now, now, 0);
    }

    // For the persistence mapper only: rebuilds a product that was saved earlier
    public static Product restore(Long id, String sku, String name, String description, BigDecimal price,
                                  int stockQuantity, CategoryRef category, Instant createdAt,
                                  Instant updatedAt, long version) {
        return new Product(id, sku, name, description, price, stockQuantity, category, createdAt,
                updatedAt, version);
    }

    // Full replace (PUT): every field is checked again
    public void changeDetails(String sku, String name, String description, BigDecimal price,
                              int stockQuantity, CategoryRef category, Instant now) {
        apply(sku, name, description, price, stockQuantity, category, now);
    }

    // Takes items out of stock for an order; fails if there are not enough of them
    public void reserve(int quantity, Instant now) {
        requirePositive(quantity);
        if (stockQuantity < quantity) {
            throw new InsufficientStockException(id, name, quantity, stockQuantity);
        }
        stockQuantity -= quantity;
        updatedAt = now;
    }

    // Puts items back, e.g. when an order is cancelled or new goods arrive
    public void release(int quantity, Instant now) {
        requirePositive(quantity);
        stockQuantity += quantity;
        updatedAt = now;
    }

    public boolean isInStock() {
        return stockQuantity > 0;
    }

    private void apply(String sku, String name, String description, BigDecimal price, int stockQuantity,
                       CategoryRef category, Instant now) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("price must be greater than 0");
        }
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("stockQuantity must be 0 or more");
        }
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.category = Objects.requireNonNull(category, "category");
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public CategoryRef getCategory() {
        return category;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
