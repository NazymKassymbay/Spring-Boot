package com.example.shop.exchange.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot.retry.autoconfigure.RetryAutoConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.example.shop.common.exception.BadRequestException;
import com.example.shop.exchange.app.ExchangeRate.Source;
import com.example.shop.exchange.config.ExchangeRateProperties;

/**
 * SIS 3: the resilience of the outbound call — retry, circuit breaker and fallback (lecture week 10, slides 24-27).
 *
 * <p>{@code @Retry} and {@code @CircuitBreaker} are aspects, so a plain {@code new ExchangeRateService(..)} would
 * test nothing: the call must go through the Spring proxy. This test therefore starts a tiny context with only
 * the service, the two Resilience4j auto-configurations and AOP — no web layer, no database, no network.
 * The external service is a Mockito mock that fails or answers as each test needs.
 */
@SpringBootTest(
        classes = ExchangeRateServiceTest.ResilienceTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // Same rules as application.properties, but no real waiting so the test stays fast
                "resilience4j.retry.instances.exchangeRate.max-attempts=3",
                "resilience4j.retry.instances.exchangeRate.wait-duration=1ms",
                "resilience4j.retry.instances.exchangeRate.retry-exceptions[0]=org.springframework.web.client.ResourceAccessException",
                "resilience4j.retry.instances.exchangeRate.retry-exceptions[1]=org.springframework.web.client.HttpServerErrorException",
                "resilience4j.circuitbreaker.instances.exchangeRate.sliding-window-size=10",
                "resilience4j.circuitbreaker.instances.exchangeRate.minimum-number-of-calls=5",
                "resilience4j.circuitbreaker.instances.exchangeRate.failure-rate-threshold=50",
                "resilience4j.circuitbreaker.instances.exchangeRate.wait-duration-in-open-state=60s",
                "resilience4j.circuitbreaker.instances.exchangeRate.record-exceptions[0]=org.springframework.web.client.ResourceAccessException",
                "resilience4j.circuitbreaker.instances.exchangeRate.record-exceptions[1]=org.springframework.web.client.HttpServerErrorException"
        })
class ExchangeRateServiceTest {

    @Configuration
    @EnableAspectJAutoProxy
    @ImportAutoConfiguration({RetryAutoConfiguration.class, CircuitBreakerAutoConfiguration.class})
    @Import(ExchangeRateService.class)
    static class ResilienceTestConfig {

        @Bean
        ExchangeRateProperties exchangeRateProperties() {
            return new ExchangeRateProperties("https://example.test", "KZT",
                    Duration.ofSeconds(2), Duration.ofSeconds(2),
                    Map.of("USD", new BigDecimal("0.0019")));
        }
    }

    @MockitoBean
    private ExchangeRateProvider provider;

    @Autowired
    private ExchangeRateService service;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    private CircuitBreaker breaker;

    @BeforeEach
    void closeTheBreaker() {
        // The context (and so the breaker) is shared by all tests: start every test from CLOSED
        breaker = circuitBreakers.circuitBreaker(ExchangeRateService.EXCHANGE_RATE);
        breaker.reset();
    }

    @Test
    void returnsTheLiveRateWhenTheProviderSucceeds() {
        given(provider.rate("KZT", "USD")).willReturn(new BigDecimal("0.0021"));

        ExchangeRate rate = service.rateFor("USD");

        assertThat(rate.source()).isEqualTo(Source.LIVE);
        assertThat(rate.rate()).isEqualByComparingTo("0.0021");
        assertThat(rate.base()).isEqualTo("KZT");
    }

    @Test
    void retriesATransientFaultAndThenReturnsTheLiveRate() {
        given(provider.rate("KZT", "USD"))
                .willThrow(new ResourceAccessException("read timed out"))
                .willReturn(new BigDecimal("0.0021"));

        ExchangeRate rate = service.rateFor("USD");

        assertThat(rate.source()).isEqualTo(Source.LIVE);
        verify(provider, times(2)).rate("KZT", "USD");
    }

    @Test
    void fallsBackAfterAllAttemptsFail() {
        given(provider.rate("KZT", "USD")).willThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        ExchangeRate rate = service.rateFor("USD");

        // The request still succeeds, but the source marks it as the fallback
        assertThat(rate.source()).isEqualTo(Source.FALLBACK);
        assertThat(rate.rate()).isEqualByComparingTo("0.0019");
        verify(provider, times(3)).rate("KZT", "USD");
    }

    @Test
    void neverRetriesAClientError() {
        given(provider.rate("KZT", "USD")).willThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        ExchangeRate rate = service.rateFor("USD");

        assertThat(rate.source()).isEqualTo(Source.FALLBACK);
        verify(provider, times(1)).rate("KZT", "USD");
    }

    @Test
    void openBreakerAnswersFromTheFallbackWithoutCallingTheService() {
        given(provider.rate("KZT", "USD")).willThrow(new ResourceAccessException("connection refused"));

        // 2 requests x 3 attempts = 6 failed calls: more than the 5-call minimum, 100 % failures -> OPEN
        service.rateFor("USD");
        service.rateFor("USD");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        org.mockito.Mockito.clearInvocations(provider);
        ExchangeRate rate = service.rateFor("USD");

        assertThat(rate.source()).isEqualTo(Source.FALLBACK);
        verify(provider, never()).rate("KZT", "USD");   // no thread waited on the dead service
    }

    @Test
    void failsWhenTheProviderIsDownAndNoFallbackExists() {
        given(provider.rate("KZT", "GBP")).willThrow(new ResourceAccessException("service down"));

        assertThatThrownBy(() -> service.rateFor("GBP")).isInstanceOf(BadRequestException.class);
    }
}
