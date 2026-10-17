package com.example.shop.order.infra;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.shop.order.domain.Order;
import com.example.shop.order.domain.OrderLine;

/**
 * Domain <-> persistence mapper (lecture weeks 6-7, slide 28).
 * Written by hand: Order has no setters and a private constructor, so it is rebuilt through restore().
 */
@Component
public class OrderEntityMapper {

    // Reads the lazy items, so it must be called inside a transaction
    public Order toDomain(OrderEntity entity) {
        List<OrderLine> lines = entity.getItems().stream()
                .map(item -> new OrderLine(item.getProductId(), item.getProductName(),
                        item.getUnitPrice(), item.getQuantity()))
                .toList();
        return Order.restore(
                entity.getId(),
                entity.getCustomerName(),
                entity.getCustomerEmail(),
                entity.getShippingAddress(),
                entity.getStatus(),
                lines,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion());
    }

    public OrderEntity toNewEntity(Order order) {
        OrderEntity entity = new OrderEntity(order.getCustomerName(), order.getCustomerEmail(),
                order.getShippingAddress());
        entity.setStatus(order.getStatus());
        entity.setCreatedAt(order.getCreatedAt());
        entity.setUpdatedAt(order.getUpdatedAt());
        for (OrderLine line : order.getLines()) {
            entity.addItem(new OrderItemEntity(line.productId(), line.productName(), line.unitPrice(),
                    line.quantity()));
        }
        return entity;
    }

    // Lines never change after an order is placed, so an update writes only status and updatedAt
    public void copyState(Order order, OrderEntity entity) {
        entity.setStatus(order.getStatus());
        entity.setUpdatedAt(order.getUpdatedAt());
    }
}
