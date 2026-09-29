package com.example.shop.exception;

// Request clashes with the current state of a resource (duplicate SKU, category still in use...) -> 409
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
