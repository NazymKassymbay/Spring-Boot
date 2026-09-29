package com.example.shop.exception;

import com.example.shop.domain.OrderStatus;

public class InvalidOrderStatusException extends ConflictException {

    public InvalidOrderStatusException(Long orderId, OrderStatus current, OrderStatus requested) {
        super("Order " + orderId + " cannot move from " + current + " to " + requested
                + " (allowed: " + current.allowedNext() + ")");
    }
}
