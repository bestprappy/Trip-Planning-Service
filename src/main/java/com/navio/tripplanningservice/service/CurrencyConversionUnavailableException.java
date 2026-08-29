package com.navio.tripplanningservice.service;

public class CurrencyConversionUnavailableException extends RuntimeException {
    public CurrencyConversionUnavailableException(String message) {
        super(message);
    }

    public CurrencyConversionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
