package com.example.shop.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Practice 7: body of {@code POST /api/v1/auth/login}. Bean Validation checks the shape only (week 4);
 * whether the password is right is decided by the authentication pipeline.
 *
 * <p>{@code toString} hides the password, so it can never end up in a log line.
 */
public record LoginRequest(
        @Schema(example = "admin") @NotBlank @Size(max = 64) String username,
        @Schema(example = "admin123") @NotBlank @Size(max = 128) String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
