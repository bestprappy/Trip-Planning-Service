package com.navio.tripplanningservice.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties("navio.currency-provider")
public record CurrencyProviderProperties(@NotNull URI baseUrl) {
}
