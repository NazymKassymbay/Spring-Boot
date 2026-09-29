package com.example.shop.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotBlank(message = "customerName is required")
        @Size(max = 100, message = "customerName must be at most 100 characters")
        String customerName,

        @NotBlank(message = "customerEmail is required")
        @Email(message = "customerEmail must be a valid email")
        String customerEmail,

        @NotBlank(message = "shippingAddress is required")
        @Size(max = 300, message = "shippingAddress must be at most 300 characters")
        String shippingAddress,

        @NotEmpty(message = "order must contain at least one item")
        List<@Valid @NotNull(message = "item must not be null") OrderItemRequest> items
) {
}
