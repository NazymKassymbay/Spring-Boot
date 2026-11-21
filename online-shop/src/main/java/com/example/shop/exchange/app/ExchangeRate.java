package com.example.shop.exchange.app;

import java.math.BigDecimal;

/**
 * One exchange rate and where it came from. {@code source} tells a client whether the number is live
 * or the fallback used while the external service was unreachable.
 */
public record ExchangeRate(String base, String code, BigDecimal rate, Source source) {

    public enum Source {
        LIVE,      // fetched from the external service just now
        FALLBACK   // the external call failed, so a configured last-known rate was used
    }
}
