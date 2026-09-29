package com.example.shop.web.dto;

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
