package com.navio.tripplanningservice.integration.mobility;

import java.util.List;
import java.util.Map;

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
        boolean stale
) {
    public MobilityEvCharger {
        connectorTypes = connectorTypes == null ? List.of() : List.copyOf(connectorTypes);
        openingHours = openingHours == null ? Map.of() : Map.copyOf(openingHours);
    }

    public record Location(double lat, double lng, String address, String placeId) {
    }
}
