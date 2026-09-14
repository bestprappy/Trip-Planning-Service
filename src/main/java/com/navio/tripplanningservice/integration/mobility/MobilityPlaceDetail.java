package com.navio.tripplanningservice.integration.mobility;

public record MobilityPlaceDetail(String name, Coordinates location, PlaceLocation placeLocation) {
    public record Coordinates(Double lat, Double lng) {}
    public record PlaceLocation(String city, String region, String countryCode, String countryName) {}
}
