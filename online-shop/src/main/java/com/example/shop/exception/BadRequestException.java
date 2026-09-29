package com.example.shop.exception;

// Request parameters are individually valid but make no sense together (e.g. minPrice > maxPrice) -> 400
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
