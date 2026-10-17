package com.example.shop.order.domain;

import com.example.shop.common.exception.ConflictException;

public class InvalidOrderStatusException extends ConflictException {

    public InvalidOrderStatusException(Long orderId, OrderStatus current, OrderStatus requested) {
        super("Order " + orderId + " cannot move from " + current + " to " + requested
                + " (allowed: " + current.allowedNext() + ")");
    }
}
