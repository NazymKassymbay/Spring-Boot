package com.example.shop.service;

import java.math.BigDecimal;

// Optional filters for the product list; null means "do not filter by this"
public record ProductFilter(
        Long categoryId,
        String query,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean inStock
) {
}
