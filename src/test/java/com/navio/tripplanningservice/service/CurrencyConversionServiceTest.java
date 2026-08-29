package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.dto.CurrencyRateResponse;
import com.navio.tripplanningservice.integration.ExchangeRate;
import com.navio.tripplanningservice.integration.ExchangeRateProvider;
import com.navio.tripplanningservice.model.CurrencyCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurrencyConversionServiceTest {

    private final ExchangeRateProvider provider = mock(ExchangeRateProvider.class);
    private final CurrencyConversionService service = new CurrencyConversionService(provider);

    @Test
    void returnsProviderRateForDifferentCurrencies() {
        LocalDate rateDate = LocalDate.of(2026, 8, 25);
        when(provider.getLatestRate(CurrencyCode.THB, CurrencyCode.USD))
                .thenReturn(new ExchangeRate(
                        CurrencyCode.THB,
                        CurrencyCode.USD,
                        new BigDecimal("0.0306"),
                        rateDate,
                        "Frankfurter"));

        CurrencyRateResponse response = service.getLatestRate(
                CurrencyCode.THB,
                CurrencyCode.USD);

        assertEquals(new BigDecimal("0.0306"), response.rate());
        assertEquals(rateDate, response.asOf());
        assertEquals("Frankfurter", response.source());
    }

    @Test
    void returnsIdentityRateWithoutCallingProvider() {
        CurrencyRateResponse response = service.getLatestRate(
                CurrencyCode.EUR,
                CurrencyCode.EUR);

        assertEquals(BigDecimal.ONE, response.rate());
        assertEquals("identity", response.source());
        verify(provider, never()).getLatestRate(CurrencyCode.EUR, CurrencyCode.EUR);
    }
}
