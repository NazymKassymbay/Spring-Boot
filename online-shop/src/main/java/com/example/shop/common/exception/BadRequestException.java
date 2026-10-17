package com.example.shop.common.exception;

// Request parameters are individually valid but make no sense together -> 400
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
