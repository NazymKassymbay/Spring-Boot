package com.example.shop.exchange.app;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.example.shop.common.exception.BadRequestException;
import com.example.shop.exchange.config.ExchangeRateProperties;

/**
 * Practice 6: the use case "give me the rate for a currency", with resilience built in
 * (lecture week 10: timeouts, retries, fallbacks).
 *
 * <p>It asks the external provider for a live rate. If that call fails for any reason — a timeout, a
 * 5xx answer, a connection error — it does not propagate the failure. Instead it returns a configured
 * last-known rate and marks the result as {@code FALLBACK}, so the shop keeps working while the
 * external service is down.
 */
@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private final ExchangeRateProvider provider;
    private final ExchangeRateProperties properties;

    public ExchangeRateService(ExchangeRateProvider provider, ExchangeRateProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    public ExchangeRate rateFor(String code) {
        String base = properties.baseCurrency();
        try {
            BigDecimal live = provider.rate(base, code);
            return new ExchangeRate(base, code, live, ExchangeRate.Source.LIVE);
        } catch (RestClientException ex) {
            // The external service failed — fall back instead of failing the request
            BigDecimal fallback = properties.fallbackRates().get(code);
            if (fallback == null) {
                throw new BadRequestException("Unsupported currency '" + code + "'");
            }
            log.warn("Exchange-rate service failed for {}->{}, using fallback {}: {}",
                    base, code, fallback, ex.getMessage());
            return new ExchangeRate(base, code, fallback, ExchangeRate.Source.FALLBACK);
        }
    }
}
