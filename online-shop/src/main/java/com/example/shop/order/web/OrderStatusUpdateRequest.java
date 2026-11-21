package com.example.shop.order.web;

import jakarta.validation.constraints.NotNull;

import com.example.shop.order.domain.OrderStatus;

public record OrderStatusUpdateRequest(
        @NotNull(message = "status is required")
        OrderStatus status
) {
}
