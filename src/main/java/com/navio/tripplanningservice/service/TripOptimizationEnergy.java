package com.navio.tripplanningservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.navio.tripplanningservice.dto.TripEvOptimizationRequest;
import com.navio.tripplanningservice.integration.mobility.MobilityEvOptimizationRequest;
import com.navio.tripplanningservice.model.Trip;

import java.util.ArrayList;
import java.util.List;

/** Resolve calculation inputs from the owned, saved trip, never request-supplied specifications. */
final class TripOptimizationEnergy {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private TripOptimizationEnergy() {}

    static MobilityEvOptimizationRequest.Vehicle vehicle(Trip trip, boolean applying) {
        if (trip.getEnergyVehicleSnapshot() == null) {
            throw new TripEvOptimizationException("Select and save a vehicle for this trip before optimizing");
        }
        JsonNode snapshot = JSON.valueToTree(trip.getEnergyVehicleSnapshot());
        JsonNode profile = snapshot.path("profile");
        String kind = profile.path("modelKind").asText();
        Double consumption = positive(profile, "consumptionKwhPer100km");
        Double capacity = positive(profile, "usableBatteryCapacityKwh");
        Double range = positive(profile, "ratedRangeKm");
        if (!("CONSUMPTION".equals(kind) && consumption != null && capacity != null)
                && !("RATED_RANGE".equals(kind) && range != null)) {
            throw new TripEvOptimizationException("The saved vehicle has insufficient data for battery predictions; confirm consumption and usable capacity or choose a rated-range preview");
        }
        if (applying) {
            if (!"CONSUMPTION".equals(kind)) {
                throw new TripEvOptimizationException("Rated-range estimates are preview-only and cannot apply automatic charging stops");
            }
            String selection = profile.path("selectionMode").asText();
            String source = profile.path("consumptionSource").asText();
            boolean eligible = switch (selection) {
                case "LEGACY_UNCONFIRMED" -> snapshot.path("legacyConsumptionConfirmed").asBoolean(false);
                case "USER_OVERRIDE" -> "USER_OBSERVED".equals(source);
                case "CATALOG_DEFAULT" -> List.of("MANUFACTURER_REPORTED", "REGULATORY_REPORTED").contains(source)
                        && "BATTERY_SIDE".equals(profile.path("consumptionMeasurementBasis").asText())
                        && profile.path("sourceUrl").asText("").startsWith("https://");
                default -> false;
            };
            if (!eligible) throw new TripEvOptimizationException("Confirm the saved vehicle energy selection before applying automatic charging stops");
        }
        var connectors = new ArrayList<String>();
        snapshot.path("connectorTypes").forEach(value -> connectors.add(value.asText()));
        if (connectors.isEmpty()) connectors.add("OTHER");
        return new MobilityEvOptimizationRequest.Vehicle(capacity, "CONSUMPTION".equals(kind) ? consumption : null,
                positive(snapshot, "maxAcKw"), positive(snapshot, "maxDcKw"), connectors,
                new TripEvOptimizationRequest.EnergyModel(kind, "CONSUMPTION".equals(kind) ? consumption : null, capacity, range));
    }

    static double initialSoc(Trip trip) {
        if (trip.getInitialSocPct() == null) {
            throw new TripEvOptimizationException("Set and save the trip starting battery before optimizing; an assumed garage value is not a confirmed trip state");
        }
        return trip.getInitialSocPct().doubleValue();
    }

    private static Double positive(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() && Double.isFinite(value.doubleValue()) && value.doubleValue() > 0
                ? value.doubleValue() : null;
    }
}
