package com.navio.tripplanningservice.integration;

import com.navio.tripplanningservice.model.CurrencyCode;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRate(
        CurrencyCode base,
        CurrencyCode quote,
        BigDecimal rate,
        LocalDate asOf,
        String source
) {
}
