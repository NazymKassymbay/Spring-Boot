package com.example.shop.auth;

/**
 * Practice 7: the answer of a successful login. The client sends {@code accessToken} back on every request
 * as {@code Authorization: Bearer <accessToken>} until it expires after {@code expiresIn} seconds.
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
