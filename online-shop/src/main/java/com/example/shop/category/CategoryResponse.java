package com.example.shop.category;

import java.time.Instant;

public record CategoryResponse(
        Long id,
        String name,
        String description,
        long productCount,
        Instant createdAt,
        Instant updatedAt
) {
}
