package com.example.shop.web.error;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

// The single error body returned by every endpoint, whatever went wrong
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> violations
) {

    public record FieldViolation(String field, Object rejectedValue, String message) {
    }
}
