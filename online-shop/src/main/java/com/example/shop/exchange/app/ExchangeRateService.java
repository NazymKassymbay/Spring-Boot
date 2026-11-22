package com.example.shop.exchange.app;

import java.math.BigDecimal;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.shop.common.exception.BadRequestException;
import com.example.shop.exchange.config.ExchangeRateProperties;

/**
 * The use case "give me the rate for a currency", with the resilience of a remote call built in
 * (lecture week 10, slides 23-27). Every remote call has three answers:
 *
 * <ol>
 *   <li><b>How long do I wait?</b> Connect and read timeouts on the RestClient ({@code RestClientConfig}).</li>
 *   <li><b>How often do I retry?</b> {@code @Retry("exchangeRate")}: up to 3 attempts with exponential backoff,
 *       only for transient faults (I/O error, timeout, 5xx). A 4xx is our mistake, so it is never retried.</li>
 *   <li><b>What do I return when it fails?</b> {@code fallbackMethod = "fallbackRate"}: a configured last-known
 *       rate, marked {@code FALLBACK}. {@code @CircuitBreaker("exchangeRate")} watches the attempts: when most
 *       recent ones failed or were slow it opens, and calls go straight to the fallback for a while
 *       ({@code CallNotPermittedException}, never retried), so no thread waits on a dead service.</li>
 * </ol>
 *
 * <p>Both annotations are aspects (weeks 8-9), so they work only when {@link #rateFor} is called through the
 * Spring proxy, i.e. from another bean such as the controller. The default aspect order is
 * Retry(CircuitBreaker(call)), so the breaker counts every single attempt. All settings live in
 * application.properties under {@code resilience4j.retry.instances.exchangeRate} and
 * {@code resilience4j.circuitbreaker.instances.exchangeRate}.
 */
@Service
public class ExchangeRateService {

    /** Name of the Resilience4j retry and circuit-breaker instances configured in application.properties. */
    public static final String EXCHANGE_RATE = "exchangeRate";

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private final ExchangeRateProvider provider;
    private final ExchangeRateProperties properties;

    public ExchangeRateService(ExchangeRateProvider provider, ExchangeRateProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    // The fallback sits on the OUTER aspect (Retry). On the inner CircuitBreaker it would turn the first failure
    // into a successful FALLBACK result, so Retry would never see a failure and never retry.
    @Retry(name = EXCHANGE_RATE, fallbackMethod = "fallbackRate")
    @CircuitBreaker(name = EXCHANGE_RATE)
    public ExchangeRate rateFor(String code) {
        String base = properties.baseCurrency();
        BigDecimal live = provider.rate(base, code);
        return new ExchangeRate(base, code, live, ExchangeRate.Source.LIVE);
    }

    /**
     * Called by the retry aspect when the call failed after all attempts, failed with a non-retryable error, or when the breaker is
     * open and the call was not even attempted ({@code CallNotPermittedException}). Same parameters plus the
     * cause, same return type (slide 26). It never fakes a live rate: the result is marked {@code FALLBACK}.
     */
    ExchangeRate fallbackRate(String code, Throwable cause) {
        String base = properties.baseCurrency();
        BigDecimal fallback = properties.fallbackRates().get(code);
        if (fallback == null) {
            throw new BadRequestException("Unsupported currency '" + code + "'");
        }
        log.warn("Exchange-rate service failed for {}->{}, using fallback {}: {}",
                base, code, fallback, cause.toString());
        return new ExchangeRate(base, code, fallback, ExchangeRate.Source.FALLBACK);
    }
}
