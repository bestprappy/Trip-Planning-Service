package com.navio.tripplanningservice.service;

public class PlaceResolutionUnavailableException extends RuntimeException {
    public PlaceResolutionUnavailableException(String message) { super(message); }
    public PlaceResolutionUnavailableException(String message, Throwable cause) { super(message, cause); }
}
