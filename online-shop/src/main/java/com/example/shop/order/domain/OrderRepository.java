package com.example.shop.order.domain;

import java.util.Optional;

/**
 * Port (lecture weeks 6-7, slide 26): one repository per aggregate root, so there is no
 * OrderItemRepository (slide 29, anti-pattern 3). order.infra.JpaOrderRepository provides it.
 */
public interface OrderRepository {

    Optional<Order> findById(long id);

    Order save(Order order);

    void deleteById(long id);
}
