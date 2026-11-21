package com.example.shop.exchange.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import com.example.shop.common.exception.BadRequestException;
import com.example.shop.exchange.app.ExchangeRate.Source;
import com.example.shop.exchange.config.ExchangeRateProperties;

/**
 * Practice 6: unit test of the resilience rule — live rate when the provider works,
 * fallback when it fails. No Spring, no network: the external provider is a Mockito mock.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock
    private ExchangeRateProvider provider;

    private ExchangeRateService service;

    @BeforeEach
    void setUp() {
        ExchangeRateProperties properties = new ExchangeRateProperties(
                "https://example.test", "KZT",
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                Map.of("USD", new BigDecimal("0.0019")));
        service = new ExchangeRateService(provider, properties);
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
    void fallsBackToTheConfiguredRateWhenTheProviderFails() {
        given(provider.rate("KZT", "USD")).willThrow(new RestClientException("service down"));

        ExchangeRate rate = service.rateFor("USD");

        // The request still succeeds, but the source marks it as the fallback
        assertThat(rate.source()).isEqualTo(Source.FALLBACK);
        assertThat(rate.rate()).isEqualByComparingTo("0.0019");
    }

    @Test
    void failsWhenTheProviderIsDownAndNoFallbackExists() {
        given(provider.rate("KZT", "GBP")).willThrow(new RestClientException("service down"));

        assertThatThrownBy(() -> service.rateFor("GBP")).isInstanceOf(BadRequestException.class);
    }
}
