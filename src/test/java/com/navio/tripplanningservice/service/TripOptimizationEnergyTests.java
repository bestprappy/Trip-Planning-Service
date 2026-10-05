package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.model.Trip;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class TripOptimizationEnergyTests {
    private Trip trip(String selection, String source, boolean confirmed) {
        var profile = new HashMap<String,Object>(Map.of("modelKind","CONSUMPTION", "consumptionKwhPer100km",18.125,
                "usableBatteryCapacityKwh",60.0,"selectionMode",selection,"consumptionSource",source));
        return Trip.builder().energyVehicleSnapshot(new HashMap<>(Map.of("profile",profile,
                "connectorTypes",List.of("CCS2"),"legacyConsumptionConfirmed",confirmed))).build();
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> profile(Trip trip) { return (Map<String,Object>)trip.getEnergyVehicleSnapshot().get("profile"); }
    @Test void legacyNeedsExplicitConfirmationAndItsNumberIsNeverRecalculated() {
        var trip=trip("LEGACY_UNCONFIRMED","UNKNOWN",false);
        assertThat(TripOptimizationEnergy.vehicle(trip,false).consumptionKwhPer100km()).isEqualTo(18.125);
        assertThatThrownBy(()->TripOptimizationEnergy.vehicle(trip,true)).hasMessageContaining("Confirm");
        trip.getEnergyVehicleSnapshot().put("legacyConsumptionConfirmed",true);
        assertThat(TripOptimizationEnergy.vehicle(trip,true).consumptionKwhPer100km()).isEqualTo(18.125);
        assertThat(profile(trip).get("consumptionSource")).isEqualTo("UNKNOWN");
    }
    @Test void catalogueRequiresDirectBatterySideEvidenceButUserObservedBasisMayBeUnknown() {
        var trip=trip("CATALOG_DEFAULT","MANUFACTURER_REPORTED",false);
        assertThatThrownBy(()->TripOptimizationEnergy.vehicle(trip,true)).hasMessageContaining("Confirm");
        profile(trip).put("sourceUrl","https://example.com/consumption");
        profile(trip).put("consumptionMeasurementBasis","WALL_SIDE");
        assertThatThrownBy(()->TripOptimizationEnergy.vehicle(trip,true)).hasMessageContaining("Confirm");
        profile(trip).put("consumptionMeasurementBasis","BATTERY_SIDE");
        assertThat(TripOptimizationEnergy.vehicle(trip,true)).isNotNull();
        assertThat(TripOptimizationEnergy.vehicle(trip("USER_OVERRIDE","USER_OBSERVED",false),true)).isNotNull();
    }
    @Test void declaredCapacityDoesNotReplaceMissingUsableCapacity() {
        var trip=trip("USER_OVERRIDE","USER_OBSERVED",false);
        profile(trip).remove("usableBatteryCapacityKwh");
        profile(trip).put("capacityBasis","MANUFACTURER_DECLARED_UNSPECIFIED");
        assertThatThrownBy(()->TripOptimizationEnergy.vehicle(trip,true)).hasMessageContaining("insufficient data");
        profile(trip).put("modelKind","RATED_RANGE");
        profile(trip).put("ratedRangeKm",480.0);
        var vehicle=TripOptimizationEnergy.vehicle(trip,false);
        assertThat(vehicle.consumptionKwhPer100km()).isNull();
        assertThat(vehicle.batteryKwh()).isNull();
        assertThat(vehicle.energyModel().ratedRangeKm()).isEqualTo(480);
    }
    @Test void missingVehicleOrTripStartingSocCannotBorrowRequestDefaults() {
        assertThatThrownBy(()->TripOptimizationEnergy.vehicle(Trip.builder().build(),false)).hasMessageContaining("Select and save");
        assertThatThrownBy(()->TripOptimizationEnergy.initialSoc(Trip.builder().build())).hasMessageContaining("starting battery");
    }
}
