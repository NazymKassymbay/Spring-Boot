package com.example.shop.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        String currency,
        int stockQuantity,
        boolean inStock,
        Long categoryId,
        String categoryName,
        Instant createdAt,
        Instant updatedAt
) {
}
