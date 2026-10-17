package com.example.shop.order.app;

import java.util.List;

// Input of the "place an order" use case. The web mapper builds it from CreateOrderRequest.
public record CreateOrderCommand(
        String customerName,
        String customerEmail,
        String shippingAddress,
        List<Line> lines
) {

    public CreateOrderCommand {
        lines = List.copyOf(lines);
    }

    public record Line(long productId, int quantity) {
    }
}
