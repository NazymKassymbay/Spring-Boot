package com.example.shop.exchange.web;

import java.math.BigDecimal;

import com.example.shop.exchange.app.ExchangeRate;

/**
 * What a client gets back. {@code source} is "LIVE" when the number came from the external service,
 * or "FALLBACK" when the service was down and a last-known rate was used.
 */
public record ExchangeRateResponse(String base, String code, BigDecimal rate, String source) {

    static ExchangeRateResponse from(ExchangeRate rate) {
        return new ExchangeRateResponse(rate.base(), rate.code(), rate.rate(), rate.source().name());
    }
}
