package com.example.shop.product.app;

import java.math.BigDecimal;

// Input of the "create product" and "replace product" use cases. The web mapper builds it from the request.
public record ProductDetails(
        String sku,
        String name,
        String description,
        BigDecimal price,
        int stockQuantity,
        long categoryId
) {
}
