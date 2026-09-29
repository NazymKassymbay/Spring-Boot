package com.example.shop.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// Used for both create (POST) and full replace (PUT)
public record ProductRequest(
        @NotBlank(message = "sku is required")
        @Pattern(regexp = "^[A-Z0-9-]{3,32}$", message = "sku must be 3-32 chars: uppercase letters, digits or '-'")
        String sku,

        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.01", message = "price must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "price must have at most 2 decimal places")
        BigDecimal price,

        @NotNull(message = "stockQuantity is required")
        @PositiveOrZero(message = "stockQuantity must be 0 or more")
        Integer stockQuantity,

        @NotNull(message = "categoryId is required")
        Long categoryId
) {
}
