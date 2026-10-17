package com.example.shop.order.infra;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.shop.order.domain.OrderStatus;
import com.example.shop.product.app.ProductUsage;

// Answers the product context's question "does an active order still hold this product?"
@Component
class JpaProductUsage implements ProductUsage {

    // Orders in these states still "hold" their products, so such products cannot be deleted
    private static final Set<OrderStatus> ACTIVE_ORDER_STATUSES = Set.of(OrderStatus.NEW, OrderStatus.PAID);

    private final SpringDataOrders orders;

    JpaProductUsage(SpringDataOrders orders) {
        this.orders = orders;
    }

    @Override
    public boolean isInActiveOrder(long productId) {
        return orders.existsByStatusInAndProductId(ACTIVE_ORDER_STATUSES, productId);
    }
}
