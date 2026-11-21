package com.example.shop.exchange.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shop.common.exception.BadRequestException;
import com.example.shop.common.web.ErrorResponse;
import com.example.shop.exchange.app.ExchangeRateService;

/**
 * Practice 6: exposes the exchange rate. The @Tag, @Operation and @ApiResponse annotations are read by
 * springdoc and shown in Swagger UI, including the error body schema (lecture week 10, slides 10 and 30).
 * A malformed code returns 400; a code that the external service and the fallback both lack also returns 400.
 */
@Tag(name = "Exchange rates", description = "The shop's currency converted to another, via an external service")
@RestController
@RequestMapping("/api/v1/exchange-rates")
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    public ExchangeRateController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    // GET /api/v1/exchange-rates/USD -> { base: "KZT", code: "USD", rate: 0.0021, source: "LIVE" | "FALLBACK" }
    @Operation(summary = "Rate of the shop currency in the given currency (3-letter ISO-4217 code, e.g. USD)")
    @ApiResponse(responseCode = "200", description = "The rate, live from the service or from the fallback")
    @ApiResponse(responseCode = "400", description = "Unknown or malformed currency code",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{code}")
    public ExchangeRateResponse rate(@PathVariable String code) {
        if (code == null || !code.matches("[A-Za-z]{3}")) {
            throw new BadRequestException("code must be a 3-letter currency, e.g. USD");
        }
        return ExchangeRateResponse.from(exchangeRateService.rateFor(code.toUpperCase()));
    }
}
