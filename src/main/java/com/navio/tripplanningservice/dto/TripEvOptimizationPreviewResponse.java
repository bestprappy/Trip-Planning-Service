package com.navio.tripplanningservice.dto;

import java.util.List;
import java.util.Map;

public record TripEvOptimizationPreviewResponse(
        long baseVersion,
        String blockId,
        boolean feasible,
        List<Operation> operations,
        Double finalSocPct,
        long totalDrivingSeconds,
        double totalChargingMinutes,
        String message,
        List<String> warnings
) {
    public TripEvOptimizationPreviewResponse {
        operations = operations == null ? List.of() : List.copyOf(operations);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public record Operation(
            String type,
            String oldItemId,
            String beforeItemId,
            int sequence,
            Charger charger,
            double estimatedChargeMinutes,
            double arrivalSocPct,
            double departureSocPct,
            double detourKm,
            String reason
    ) {
    }

    public record Charger(
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
        public Charger {
            connectorTypes = connectorTypes == null ? List.of() : List.copyOf(connectorTypes);
            openingHours = openingHours == null ? Map.of() : Map.copyOf(openingHours);
        }
    }

    public record Location(double lat, double lng, String address, String placeId) {
    }
}
