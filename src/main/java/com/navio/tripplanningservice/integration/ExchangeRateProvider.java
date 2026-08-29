package com.navio.tripplanningservice.integration;

import com.navio.tripplanningservice.model.CurrencyCode;

public interface ExchangeRateProvider {
    ExchangeRate getLatestRate(CurrencyCode base, CurrencyCode quote);
}
