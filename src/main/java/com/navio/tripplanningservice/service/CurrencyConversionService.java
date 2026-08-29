package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.dto.CurrencyRateResponse;
import com.navio.tripplanningservice.integration.ExchangeRate;
import com.navio.tripplanningservice.integration.ExchangeRateProvider;
import com.navio.tripplanningservice.model.CurrencyCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CurrencyConversionService {

    private final ExchangeRateProvider exchangeRateProvider;

    public CurrencyRateResponse getLatestRate(CurrencyCode base, CurrencyCode quote) {
        if (base == quote) {
            return new CurrencyRateResponse(
                    base,
                    quote,
                    BigDecimal.ONE,
                    LocalDate.now(),
                    "identity");
        }

        ExchangeRate exchangeRate = exchangeRateProvider.getLatestRate(base, quote);
        return new CurrencyRateResponse(
                exchangeRate.base(),
                exchangeRate.quote(),
                exchangeRate.rate(),
                exchangeRate.asOf(),
                exchangeRate.source());
    }
}
