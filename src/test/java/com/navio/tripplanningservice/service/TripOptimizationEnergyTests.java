package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.model.Trip;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class TripOptimizationEnergyTests {
    private static final String VEHICLE_ID = "6f2c1f6e-6f0a-4d8e-9a43-3b1f5c9d2a10";

    private Trip trip(String selection, String source, boolean confirmed) {
        var profile = new HashMap<String,Object>(Map.of("modelKind","CONSUMPTION", "consumptionKwhPer100km",18.125,
                "usableBatteryCapacityKwh",60.0,"selectionMode",selection,"consumptionSource",source));
        return Trip.builder().energyVehicleSnapshot(new HashMap<>(Map.of("vehicleId",VEHICLE_ID,"profile",profile,
                "connectorTypes",List.of("CCS2"),"legacyConsumptionConfirmed",confirmed))).build();
    }
    private Trip navioEstimate(Double usableCapacity) {
        var profile = new HashMap<String,Object>(Map.of("modelKind","RATED_RANGE","ratedRangeKm",480.0,
                "selectionMode","CATALOG_DEFAULT","consumptionSource","UNKNOWN"));
        if (usableCapacity != null) profile.put("usableBatteryCapacityKwh", usableCapacity);
        return Trip.builder().energyVehicleSnapshot(new HashMap<>(Map.of("vehicleId",VEHICLE_ID,"profile",profile,
                "connectorTypes",List.of("CCS2"),"legacyConsumptionConfirmed",false))).build();
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> profile(Trip trip) { return (Map<String,Object>)trip.getEnergyVehicleSnapshot().get("profile"); }

    @Test void everySavedConsumptionChoicePlansChargingWithItsOwnNumber() {
        for (var selection : List.of("LEGACY_UNCONFIRMED", "CATALOG_DEFAULT", "USER_OVERRIDE")) {
            var vehicle = TripOptimizationEnergy.vehicle(trip(selection, "UNKNOWN", false));
            assertThat(vehicle.consumptionKwhPer100km()).isEqualTo(18.125);
            assertThat(vehicle.energyModel().modelKind()).isEqualTo("CONSUMPTION");
        }
    }
    @Test void aNavioEstimatePlansChargingFromRatedRange() {
        var vehicle = TripOptimizationEnergy.vehicle(navioEstimate(64.0));
        assertThat(vehicle.consumptionKwhPer100km()).isNull();
        assertThat(vehicle.batteryKwh()).isEqualTo(64.0);
        assertThat(vehicle.energyModel().modelKind()).isEqualTo("RATED_RANGE");
        assertThat(vehicle.energyModel().ratedRangeKm()).isEqualTo(480.0);
    }
    @Test void aMissingUsableCapacityFallsBackToTheTripVehiclesDeclaredBattery() {
        var trip = navioEstimate(null);
        trip.setTripVehicles(List.of(Map.of("scope","account","vehicle",Map.of("id",VEHICLE_ID,"batteryCapacityKwh",77.4))));
        var vehicle = TripOptimizationEnergy.vehicle(trip);
        assertThat(vehicle.batteryKwh()).isEqualTo(77.4);
        assertThat(vehicle.energyModel().usableBatteryCapacityKwh()).isEqualTo(77.4);
    }
    @Test void withNoBatterySizeAnywhereTheTravellerIsToldToChooseAnEnergyOption() {
        assertThatThrownBy(() -> TripOptimizationEnergy.vehicle(navioEstimate(null))).hasMessageContaining("insufficient data");
        var trip = trip("USER_OVERRIDE","USER_OBSERVED",false);
        profile(trip).remove("consumptionKwhPer100km");
        assertThatThrownBy(() -> TripOptimizationEnergy.vehicle(trip)).hasMessageContaining("Energy Consumption");
    }
    @Test void anUnsavedTripStartingBatteryUsesTheSelectedVehiclesStartingBattery() {
        var trip = navioEstimate(60.0);
        trip.setTripVehicles(List.of(Map.of("scope","account","vehicle",Map.of("id",VEHICLE_ID,"settings",Map.of("startingBatteryPct",85)))));
        assertThat(TripOptimizationEnergy.initialSoc(trip)).isEqualTo(85.0);
        trip.setInitialSocPct(new java.math.BigDecimal("42.5"));
        assertThat(TripOptimizationEnergy.initialSoc(trip)).isEqualTo(42.5);
    }
    @Test void missingVehicleOrTripStartingSocCannotBorrowRequestDefaults() {
        assertThatThrownBy(()->TripOptimizationEnergy.vehicle(Trip.builder().build())).hasMessageContaining("Select and save");
        assertThatThrownBy(()->TripOptimizationEnergy.initialSoc(Trip.builder().build())).hasMessageContaining("starting battery");
    }
}
