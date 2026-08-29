package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.CurrencyRateResponse;
import com.navio.tripplanningservice.model.CurrencyCode;
import com.navio.tripplanningservice.service.CurrencyConversionService;
import org.junit.jupiter.api.Test;
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
class CurrencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrencyConversionService currencyConversionService;

    @Test
    void returnsLatestCurrencyRate() throws Exception {
        when(currencyConversionService.getLatestRate(CurrencyCode.THB, CurrencyCode.USD))
                .thenReturn(new CurrencyRateResponse(
                        CurrencyCode.THB,
                        CurrencyCode.USD,
                        new BigDecimal("0.0306"),
                        LocalDate.of(2026, 8, 25),
                        "Frankfurter"));

        mockMvc.perform(get("/v1/trips/currencies/rate")
                        .queryParam("base", "THB")
                        .queryParam("quote", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.base").value("THB"))
                .andExpect(jsonPath("$.quote").value("USD"))
                .andExpect(jsonPath("$.rate").value(0.0306))
                .andExpect(jsonPath("$.asOf").value("2026-08-25"))
                .andExpect(jsonPath("$.source").value("Frankfurter"));
    }

    @Test
    void rejectsUnsupportedCurrencyCode() throws Exception {
        mockMvc.perform(get("/v1/trips/currencies/rate")
                        .queryParam("base", "THB")
                        .queryParam("quote", "GBP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request is invalid"));
    }
}
