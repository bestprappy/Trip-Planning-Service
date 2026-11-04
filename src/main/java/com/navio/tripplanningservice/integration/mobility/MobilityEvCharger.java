package com.navio.tripplanningservice.integration.mobility;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record MobilityEvCharger(
        String id,
        String name,
        String operatorName,
        Location location,
        String address,
        String province,
        List<String> connectorTypes,
        double maxKw,
        int totalConnectors,
        Integer availableConnectors,
        String priceText,
        Map<String, Object> openingHours,
        String source,
        String verificationStatus,
        String status,
        double ratingAvg,
        long ratingCount,
        double confidenceScore,
        boolean stale,
        Map<String, Double> connectorPowerKw,
        Instant statusCheckedAt
) {
    public MobilityEvCharger(String id, String name, String operatorName, Location location, String address, String province,
                             List<String> connectorTypes, double maxKw, int totalConnectors, Integer availableConnectors, String priceText,
                             Map<String, Object> openingHours, String source, String verificationStatus, String status,
                             double ratingAvg, long ratingCount, double confidenceScore, boolean stale) {
        this(id, name, operatorName, location, address, province, connectorTypes, maxKw, totalConnectors, availableConnectors, priceText,
                openingHours, source, verificationStatus, status, ratingAvg, ratingCount, confidenceScore, stale, Map.of(), null);
    }

    public MobilityEvCharger {
        connectorPowerKw = connectorPowerKw == null ? Map.of() : Map.copyOf(connectorPowerKw);
        connectorTypes = connectorTypes == null ? List.of() : List.copyOf(connectorTypes);
        // Providers often omit fields such as openNow, which arrive as null values Map.copyOf rejects.
        openingHours = openingHours == null ? Map.of() : openingHours.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public record Location(double lat, double lng, String address, String placeId) {
    }
}
