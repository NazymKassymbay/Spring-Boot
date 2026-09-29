package com.example.shop.exception;

public class InsufficientStockException extends BusinessRuleException {

    public InsufficientStockException(Long productId, String productName, int requested, int available) {
        super("Not enough stock for product " + productId + " (" + productName + "): requested "
                + requested + ", available " + available);
    }
}
