package com.example.shop.common.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * Practice 7: OpenAPI meta-annotation for an endpoint restricted to the ADMIN role: 401 without a valid token,
 * 403 with a valid token of a user who is not an admin (lecture week 12, slide 6).
 * It documents only; the rule itself lives in SecurityConfig.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(responseCode = "401", description = "No Bearer token, or an invalid or expired one",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "Signed in, but without the ADMIN role",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public @interface ApiAdminOnly {
}
