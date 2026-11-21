package com.example.shop.order.events;

/**
 * Thrown by the consumer when an {@link OrderPlaced} record is structurally invalid (lecture week 11, slide 28).
 * The Kafka error handler treats it as non-retryable: such a "poison" record is sent straight to the
 * dead-letter topic instead of being retried, because retrying a permanently broken record only blocks the
 * partition.
 */
public class InvalidOrderEventException extends RuntimeException {

    public InvalidOrderEventException(String message) {
        super(message);
    }
}
