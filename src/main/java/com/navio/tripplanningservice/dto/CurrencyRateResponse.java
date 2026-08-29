package com.navio.tripplanningservice.dto;

import com.navio.tripplanningservice.model.CurrencyCode;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CurrencyRateResponse(
        CurrencyCode base,
        CurrencyCode quote,
        BigDecimal rate,
        LocalDate asOf,
        String source
) {
}
