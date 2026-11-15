package com.navio.tripplanningservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.navio.tripplanningservice.dto.TripEvOptimizationRequest;
import com.navio.tripplanningservice.integration.mobility.MobilityEvOptimizationRequest;
import com.navio.tripplanningservice.model.Trip;

import java.util.ArrayList;

/** Resolve calculation inputs from the owned, saved trip, never request-supplied specifications. */
final class TripOptimizationEnergy {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private TripOptimizationEnergy() {}

    /**
     * Any saved energy model that can predict the battery may plan charging: the traveller's own average,
     * the catalogue figure, or the NAVIO Estimate from rated range. Without a usable capacity the
     * vehicle's declared battery size stands in, because a charge time cannot be computed from nothing.
     */
    static MobilityEvOptimizationRequest.Vehicle vehicle(Trip trip) {
        if (trip.getEnergyVehicleSnapshot() == null) {
            throw new TripEvOptimizationException("Select and save a vehicle for this trip before optimizing");
        }
        JsonNode snapshot = JSON.valueToTree(trip.getEnergyVehicleSnapshot());
        JsonNode profile = snapshot.path("profile");
        String kind = profile.path("modelKind").asText();
        Double consumption = positive(profile, "consumptionKwhPer100km");
        Double range = positive(profile, "ratedRangeKm");
        Double capacity = positive(profile, "usableBatteryCapacityKwh");
        if (capacity == null) capacity = declaredCapacity(trip, snapshot.path("vehicleId").asText(null));
        boolean predictable = ("CONSUMPTION".equals(kind) && consumption != null)
                || ("RATED_RANGE".equals(kind) && range != null);
        if (!predictable || capacity == null) {
            throw new TripEvOptimizationException("The saved vehicle has insufficient data for battery predictions. Choose an Energy Consumption option in vehicle settings and save");
        }
        var connectors = new ArrayList<String>();
        snapshot.path("connectorTypes").forEach(value -> connectors.add(value.asText()));
        if (connectors.isEmpty()) connectors.add("OTHER");
        Double modelConsumption = "CONSUMPTION".equals(kind) ? consumption : null;
        return new MobilityEvOptimizationRequest.Vehicle(capacity, modelConsumption,
                positive(snapshot, "maxAcKw"), positive(snapshot, "maxDcKw"), connectors,
                new TripEvOptimizationRequest.EnergyModel(kind, modelConsumption, capacity, "RATED_RANGE".equals(kind) ? range : null));
    }

    private static Double declaredCapacity(Trip trip, String vehicleId) {
        JsonNode vehicle = tripVehicle(trip, vehicleId);
        return vehicle == null ? null : positive(vehicle, "batteryCapacityKwh");
    }

    private static JsonNode tripVehicle(Trip trip, String vehicleId) {
        if (vehicleId == null || trip.getTripVehicles() == null) return null;
        for (var entry : trip.getTripVehicles()) {
            JsonNode vehicle = JSON.valueToTree(entry).path("vehicle");
            if (vehicleId.equals(vehicle.path("id").asText())) return vehicle;
        }
        return null;
    }

    /**
     * The battery the planner shows the trip starting with: the trip's own value, or else the selected
     * vehicle's starting battery, as the planner and published garage both fall back to.
     */
    static double initialSoc(Trip trip) {
        if (trip.getInitialSocPct() != null) return trip.getInitialSocPct().doubleValue();
        JsonNode vehicle = tripVehicle(trip, trip.getEnergyVehicleSnapshot() == null ? null
                : JSON.valueToTree(trip.getEnergyVehicleSnapshot()).path("vehicleId").asText(null));
        JsonNode starting = vehicle == null ? null : vehicle.path("settings").path("startingBatteryPct");
        if (starting != null && starting.isNumber() && starting.doubleValue() >= 0 && starting.doubleValue() <= 100) {
            return starting.doubleValue();
        }
        throw new TripEvOptimizationException("Set the trip starting battery in My garage before planning charging stops");
    }

    private static Double positive(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() && Double.isFinite(value.doubleValue()) && value.doubleValue() > 0
                ? value.doubleValue() : null;
    }
}
