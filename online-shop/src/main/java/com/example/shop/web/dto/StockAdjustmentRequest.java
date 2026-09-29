package com.example.shop.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Positive delta = goods arrived, negative delta = write-off
public record StockAdjustmentRequest(
        @NotNull(message = "delta is required")
        @Min(value = -10000, message = "delta must be >= -10000")
        @Max(value = 10000, message = "delta must be <= 10000")
        Integer delta,

        @Size(max = 200, message = "reason must be at most 200 characters")
        String reason
) {
}
