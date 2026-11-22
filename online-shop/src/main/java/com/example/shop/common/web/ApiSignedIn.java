package com.example.shop.common.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * Practice 7: OpenAPI meta-annotation for an endpoint that needs any signed-in user. It documents the 401 that
 * the security filter chain returns without a valid Bearer token, so the same two lines are not repeated on
 * every such endpoint (lecture week 10, slide 10: "repeated responses -> a meta-annotation").
 * It documents only; the rule itself lives in SecurityConfig.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(responseCode = "401", description = "No Bearer token, or an invalid or expired one",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public @interface ApiSignedIn {
}
