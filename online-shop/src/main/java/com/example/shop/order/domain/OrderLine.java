package com.example.shop.order.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object (lecture weeks 6-7, slide 20): one line of an order. Immutable, self-validating, equal by value.
 * Name and price are copied from the product at order time, so later price changes or
 * product deletion do not rewrite order history. The product is referenced by id only (slide 21).
 */
public record OrderLine(long productId, String productName, BigDecimal unitPrice, int quantity) {

    public OrderLine {                                  // compact constructor: runs on every creation
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("unitPrice must be greater than 0");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
