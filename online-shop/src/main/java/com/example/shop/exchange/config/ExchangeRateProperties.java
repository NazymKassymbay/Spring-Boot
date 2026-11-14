package com.example.shop.exchange.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Practice 6: typed settings for the external exchange-rate call (lecture week 2 style, reused here).
 *
 * @param baseUrl         base URL of the external service
 * @param baseCurrency    the shop's own currency, e.g. KZT
 * @param connectTimeout  how long to wait to open the connection
 * @param readTimeout     how long to wait for the response body
 * @param fallbackRates   last-known rates used when the external call fails, keyed by currency code
 */
@Validated
@ConfigurationProperties("app.exchange")
public record ExchangeRateProperties(
        @NotBlank String baseUrl,
        @NotBlank String baseCurrency,
        Duration connectTimeout,
        Duration readTimeout,
        Map<String, BigDecimal> fallbackRates) {

    public ExchangeRateProperties {
        // Safe defaults, so a missing property never leaves a null timeout or map
        connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(2);
        readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(2);
        fallbackRates = fallbackRates != null ? Map.copyOf(fallbackRates) : Map.of();
    }
}
