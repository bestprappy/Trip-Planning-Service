package com.navio.tripplanningservice.service;

public class MobilityOptimizationUnavailableException extends RuntimeException {

    public MobilityOptimizationUnavailableException(String message) {
        super(message);
    }

    public MobilityOptimizationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
