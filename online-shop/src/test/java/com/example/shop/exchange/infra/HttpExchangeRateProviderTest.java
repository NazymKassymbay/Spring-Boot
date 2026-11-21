package com.example.shop.exchange.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.shop.exchange.app.ExchangeRateProvider;

/**
 * Practice 6: tests the real HTTP adapter against a mock server (lecture week 10, slide 28).
 * It proves both paths: a good response is parsed, and a server error becomes a RestClientException,
 * which the service layer then turns into a fallback. No real network is used.
 */
class HttpExchangeRateProviderTest {

    private MockRestServiceServer server;
    private ExchangeRateProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://fx.test");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new HttpExchangeRateProvider(builder.build());
    }

    @Test
    void parsesTheRequestedRateFromTheResponse() {
        server.expect(requestTo("https://fx.test/KZT"))
                .andRespond(withSuccess("""
                        {"result":"success","rates":{"USD":0.0021,"EUR":0.0019}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.rate("KZT", "USD")).isEqualByComparingTo("0.0021");
        server.verify();
    }

    @Test
    void throwsWhenTheServiceReturnsAnError() {
        server.expect(requestTo("https://fx.test/KZT")).andRespond(withServerError());

        assertThatThrownBy(() -> provider.rate("KZT", "USD")).isInstanceOf(RestClientException.class);
    }

    @Test
    void throwsWhenTheResponseHasNoRateForTheCode() {
        server.expect(requestTo("https://fx.test/KZT"))
                .andRespond(withSuccess("{\"result\":\"success\",\"rates\":{\"EUR\":0.0019}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.rate("KZT", "USD")).isInstanceOf(RestClientException.class);
    }
}
