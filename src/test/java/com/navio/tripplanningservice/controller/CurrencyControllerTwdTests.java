package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.CurrencyRateResponse;
import com.navio.tripplanningservice.model.CurrencyCode;
import com.navio.tripplanningservice.service.CurrencyConversionService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CurrencyController.class)
class CurrencyControllerTwdTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrencyConversionService currencyConversionService;

    @ParameterizedTest
    @CsvSource({"THB,TWD", "TWD,THB", "USD,TWD", "TWD,USD", "EUR,TWD", "TWD,EUR", "JPY,TWD", "TWD,JPY"})
    void acceptsTwdAsBaseAndQuote(CurrencyCode base, CurrencyCode quote) throws Exception {
        when(currencyConversionService.getLatestRate(base, quote))
                .thenReturn(new CurrencyRateResponse(base, quote, new BigDecimal("0.95"),
                        LocalDate.of(2026, 10, 9), "Frankfurter"));

        mockMvc.perform(get("/v1/trips/currencies/rate")
                        .queryParam("base", base.name())
                        .queryParam("quote", quote.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.base").value(base.name()))
                .andExpect(jsonPath("$.quote").value(quote.name()))
                .andExpect(jsonPath("$.rate").value(0.95));
    }
}
