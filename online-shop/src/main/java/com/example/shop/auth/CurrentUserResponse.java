package com.example.shop.auth;

import java.time.Instant;
import java.util.List;

/** Practice 7: who the caller is, read from the validated token: no database query (slide 21). */
public record CurrentUserResponse(String username, List<String> roles, Instant expiresAt) {
}
