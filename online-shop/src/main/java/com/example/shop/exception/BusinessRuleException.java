package com.example.shop.exception;

// Request is well-formed but breaks a business rule (not enough stock, too many items...) -> 409
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
