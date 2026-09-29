package com.example.shop.mapper;

import org.springframework.stereotype.Component;

import com.example.shop.config.ShopProperties;
import com.example.shop.domain.Order;
import com.example.shop.domain.OrderItem;
import com.example.shop.web.dto.OrderItemResponse;
import com.example.shop.web.dto.OrderResponse;

@Component
public class OrderMapper {

    private final ShopProperties shopProperties;

    public OrderMapper(ShopProperties shopProperties) {
        this.shopProperties = shopProperties;
    }

    public OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getShippingAddress(),
                order.getStatus(),
                order.getItems().stream().map(this::toItemResponse).toList(),
                order.getTotalQuantity(),
                order.getTotalAmount(),
                shopProperties.getCurrency(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getProductId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal());
    }
}
