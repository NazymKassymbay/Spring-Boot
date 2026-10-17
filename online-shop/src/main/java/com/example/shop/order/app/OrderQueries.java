package com.example.shop.order.app;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.shop.order.domain.Order;
import com.example.shop.order.domain.OrderStatus;

// Read side: paged lists. Kept out of the domain port, because Page and Pageable are Spring types.
public interface OrderQueries {

    // One page of orders; status == null means all statuses
    Page<Order> page(OrderStatus status, Pageable pageable);
}
