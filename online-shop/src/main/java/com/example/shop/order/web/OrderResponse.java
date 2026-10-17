package com.example.shop.order.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.example.shop.order.domain.OrderStatus;

public record OrderResponse(
        Long id,
        String customerName,
        String customerEmail,
        String shippingAddress,
        OrderStatus status,
        List<OrderItemResponse> items,
        int totalQuantity,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt,
        Instant updatedAt
) {
}
