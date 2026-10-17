package com.example.shop.order.web;

import org.springframework.stereotype.Component;

import com.example.shop.common.config.ShopProperties;
import com.example.shop.order.app.CreateOrderCommand;
import com.example.shop.order.domain.Order;
import com.example.shop.order.domain.OrderLine;

/**
 * API <-> domain mapper (lecture weeks 6-7, slide 28).
 * The request becomes use-case input, and the domain object becomes the response.
 */
@Component
public class OrderApiMapper {

    private final ShopProperties shopProperties;

    public OrderApiMapper(ShopProperties shopProperties) {
        this.shopProperties = shopProperties;
    }

    public CreateOrderCommand toCommand(CreateOrderRequest request) {
        return new CreateOrderCommand(
                request.customerName().trim(),
                request.customerEmail().trim(),
                request.shippingAddress().trim(),
                request.items().stream()
                        .map(item -> new CreateOrderCommand.Line(item.productId(), item.quantity()))
                        .toList());
    }

    public OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getShippingAddress(),
                order.getStatus(),
                order.getLines().stream().map(this::toItemResponse).toList(),
                order.getTotalQuantity(),
                order.getTotalAmount(),
                shopProperties.getCurrency(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private OrderItemResponse toItemResponse(OrderLine line) {
        return new OrderItemResponse(
                line.productId(),
                line.productName(),
                line.unitPrice(),
                line.quantity(),
                line.lineTotal());
    }
}
