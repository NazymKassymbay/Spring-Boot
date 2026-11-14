package com.example.shop.exchange.infra;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.shop.exchange.app.ExchangeRateProvider;

/**
 * Practice 6: the real adapter. It calls the external service through the timed RestClient
 * (see RestClientConfig). On any transport error RestClient throws a RestClientException, which the
 * service layer turns into a fallback. The URL shape fits open.er-api.com: GET {baseUrl}/{base}.
 */
@Component
class HttpExchangeRateProvider implements ExchangeRateProvider {

    private final RestClient restClient;

    HttpExchangeRateProvider(RestClient exchangeRateRestClient) {
        this.restClient = exchangeRateRestClient;
    }

    @Override
    public BigDecimal rate(String base, String code) throws RestClientException {
        // retrieve().body(...) already throws RestClientException on a 4xx/5xx or an I/O/timeout error
        RatesResponse response = restClient.get()
                .uri("/{base}", base)
                .retrieve()
                .body(RatesResponse.class);

        if (response == null || response.rates() == null || !response.rates().containsKey(code)) {
            throw new RestClientException("No rate for " + code + " in the external response");
        }
        return response.rates().get(code);
    }

    // Only the fields we use; everything else in the JSON is ignored
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RatesResponse(String result, Map<String, BigDecimal> rates) {
    }
}
