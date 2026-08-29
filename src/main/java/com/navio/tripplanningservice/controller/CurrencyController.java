package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.CurrencyRateResponse;
import com.navio.tripplanningservice.model.CurrencyCode;
import com.navio.tripplanningservice.service.CurrencyConversionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/trips/currencies")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyConversionService currencyConversionService;

    @GetMapping("/rate")
    public ResponseEntity<CurrencyRateResponse> getRate(
            @RequestParam CurrencyCode base,
            @RequestParam CurrencyCode quote) {
        return ResponseEntity.ok(currencyConversionService.getLatestRate(base, quote));
    }
}
