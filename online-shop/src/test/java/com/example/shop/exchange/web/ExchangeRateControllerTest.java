package com.example.shop.exchange.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.shop.common.config.AppConfig;
import com.example.shop.exchange.app.ExchangeRate;
import com.example.shop.exchange.app.ExchangeRateService;

/**
 * Practice 6: web-layer slice of the exchange-rate endpoint. The service is mocked, so this checks
 * mapping, the "source" field and validation — not the external call.
 */
@WebMvcTest(ExchangeRateController.class)
@Import(AppConfig.class)   // Clock for the error handler
class ExchangeRateControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ExchangeRateService exchangeRateService;

    @Test
    void returnsTheRateAndUppercasesTheCode() throws Exception {
        given(exchangeRateService.rateFor("USD"))
                .willReturn(new ExchangeRate("KZT", "USD", new BigDecimal("0.0021"), ExchangeRate.Source.LIVE));

        mvc.perform(get("/api/v1/exchange-rates/{code}", "usd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.base").value("KZT"))
                .andExpect(jsonPath("$.code").value("USD"))
                .andExpect(jsonPath("$.rate").value(0.0021))
                .andExpect(jsonPath("$.source").value("LIVE"));

        // the path "usd" was uppercased to "USD" before the service call
        verify(exchangeRateService).rateFor(eq("USD"));
    }

    @Test
    void marksAFallbackResult() throws Exception {
        given(exchangeRateService.rateFor("EUR"))
                .willReturn(new ExchangeRate("KZT", "EUR", new BigDecimal("0.0017"), ExchangeRate.Source.FALLBACK));

        mvc.perform(get("/api/v1/exchange-rates/{code}", "EUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("FALLBACK"));
    }

    @Test
    void rejectsACodeThatIsNotThreeLetters() throws Exception {
        mvc.perform(get("/api/v1/exchange-rates/{code}", "US1"))
                .andExpect(status().isBadRequest());
    }
}
