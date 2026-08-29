package com.navio.tripplanningservice.integration;

import com.navio.tripplanningservice.config.CurrencyProviderProperties;
import com.navio.tripplanningservice.model.CurrencyCode;
import com.navio.tripplanningservice.service.CurrencyConversionUnavailableException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class FrankfurterExchangeRateProvider implements ExchangeRateProvider {

    private static final String SOURCE = "Frankfurter";

    private final RestClient restClient;

    public FrankfurterExchangeRateProvider(
            RestClient.Builder restClientBuilder,
            CurrencyProviderProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl().toString())
                .build();
    }

    @Override
    @Cacheable(cacheNames = "currencyRates", key = "#base.name() + '-' + #quote.name()", sync = true)
    public ExchangeRate getLatestRate(CurrencyCode base, CurrencyCode quote) {
        try {
            FrankfurterRateResponse response = restClient.get()
                    .uri("/v2/rate/{base}/{quote}", base.name(), quote.name())
                    .retrieve()
                    .body(FrankfurterRateResponse.class);
            if (response == null
                    || response.rate() == null
                    || response.rate().signum() <= 0
                    || response.date() == null
                    || !base.name().equals(response.base())
                    || !quote.name().equals(response.quote())) {
                throw new CurrencyConversionUnavailableException(
                        "The exchange-rate provider returned an invalid rate");
            }
            return new ExchangeRate(base, quote, response.rate(), response.date(), SOURCE);
        } catch (RestClientException exception) {
            throw new CurrencyConversionUnavailableException(
                    "The exchange-rate provider is unavailable", exception);
        }
    }

    private record FrankfurterRateResponse(
            LocalDate date,
            String base,
            String quote,
            BigDecimal rate
    ) {
    }
}
