package com.navio.tripplanningservice.integration.mobility;

import java.util.List;

public record MobilityEvOptimizationRequest(
        String blockId,
        List<Stop> stops,
        Vehicle vehicle,
        double startingSocPct,
        double reserveSocPct,
        double targetSocPct,
        double maximumDetourKm
) {
    public MobilityEvOptimizationRequest {
        stops = List.copyOf(stops);
    }

    public record Stop(
            String itemId,
            String name,
            double lat,
            double lng,
            MobilityEvCharger charger,
            boolean locked,
            String selectionSource,
            Integer targetBatteryPct,
            Double observedSocPct
    ) {
        public Stop(String itemId, String name, double lat, double lng, MobilityEvCharger charger, boolean locked, String selectionSource, Integer targetBatteryPct) {
            this(itemId, name, lat, lng, charger, locked, selectionSource, targetBatteryPct, null);
        }
        public Stop(String itemId, String name, double lat, double lng, MobilityEvCharger charger, boolean locked, String selectionSource) {
            this(itemId, name, lat, lng, charger, locked, selectionSource, null);
        }
    }

    public record Vehicle(
            Double batteryKwh,
            Double consumptionKwhPer100km,
            Double maxAcKw,
            Double maxDcKw,
            List<String> connectorTypes,
            com.navio.tripplanningservice.dto.TripEvOptimizationRequest.EnergyModel energyModel
    ) {
        public Vehicle(double batteryKwh, double consumptionKwhPer100km, double maxAcKw, double maxDcKw, List<String> connectorTypes) {
            this(batteryKwh, consumptionKwhPer100km, maxAcKw, maxDcKw, connectorTypes, null);
        }
        public Vehicle {
            connectorTypes = List.copyOf(connectorTypes);
        }
    }
}
