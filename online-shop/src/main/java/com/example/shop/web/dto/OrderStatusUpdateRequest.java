package com.example.shop.web.dto;

import jakarta.validation.constraints.NotNull;

import com.example.shop.domain.OrderStatus;

public record OrderStatusUpdateRequest(
        @NotNull(message = "status is required")
        OrderStatus status
) {
}
