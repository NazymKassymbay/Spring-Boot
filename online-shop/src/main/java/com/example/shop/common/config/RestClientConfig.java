package com.example.shop.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.example.shop.exchange.config.ExchangeRateProperties;

/**
 * Practice 6: a RestClient for the exchange-rate service with explicit connect and read timeouts
 * (lecture week 10: "resilience of remote calls — timeouts, retries, fallbacks").
 *
 * <p>Without a read timeout a slow remote server would hold our thread (and its pool slot) until the
 * TCP socket gave up — minutes. A short timeout turns a hanging dependency into a fast failure that the
 * service layer then answers with a fallback value.
 *
 * <p>The lecture (slide 20) injects Boot's {@code RestClient.Builder}; Spring Boot 4 does not expose
 * that as a bean by default, so the client is built straight from {@code RestClient.builder()}.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient exchangeRateRestClient(ExchangeRateProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());   // time to open the socket
        factory.setReadTimeout(properties.readTimeout());         // time to wait for the response
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }
}
