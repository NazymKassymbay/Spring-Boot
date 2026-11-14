package com.example.shop.exchange.app;

import java.math.BigDecimal;

import org.springframework.web.client.RestClientException;

/**
 * Port to the external exchange-rate service. The infra adapter (HttpExchangeRateProvider) calls the
 * real HTTP API; a test provides a simple in-memory or throwing version instead.
 */
public interface ExchangeRateProvider {

    /**
     * @return how much one unit of {@code base} is worth in {@code code}
     * @throws RestClientException if the service is unreachable, times out, answers an error,
     *                             or has no rate for {@code code}
     */
    BigDecimal rate(String base, String code) throws RestClientException;
}
