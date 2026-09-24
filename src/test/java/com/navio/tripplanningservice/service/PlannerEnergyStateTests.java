package com.navio.tripplanningservice.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.navio.tripplanningservice.dto.PlannerSnapshotRequest;
import com.navio.tripplanningservice.dto.PlannerItemDto;
import com.navio.tripplanningservice.model.Trip;
import com.navio.tripplanningservice.model.BlockItem;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PlannerEnergyStateTests {
    @Test void tripGarageRoundTripsPreservesOmissionAndClearsExplicitly() throws Exception {
        var trip = new Trip();
        var id = "11111111-1111-4111-8111-111111111111";
        PlannerEnergyState.apply(trip, json.readTree("{\"garageVehicleIds\":[\"" + id + "\"]}"));
        PlannerEnergyState.apply(trip, json.readTree("{\"initialSocPct\":50}"));
        assertThat(trip.getGarageVehicleIds()).containsExactly(id);
        var other = new Trip();
        PlannerEnergyState.apply(other, PlannerEnergyState.read(trip));
        assertThat(other.getGarageVehicleIds()).containsExactly(id);
        PlannerEnergyState.apply(other, json.readTree("{\"garageVehicleIds\":[]}"));
        assertThat(other.getGarageVehicleIds()).isEmpty();
        assertThat(trip.getGarageVehicleIds()).containsExactly(id);
        for (String invalid : new String[]{"null", "[\"bad\"]", "[\"" + id + "\",\"" + id + "\"]"}) {
            var state = json.readTree("{\"garageVehicleIds\":" + invalid + "}");
            assertThatThrownBy(() -> PlannerEnergyState.apply(new Trip(), state)).isInstanceOf(PlannerService.PlannerValidationException.class);
        }
    }
    @Test void unknownChargingDurationIsAcceptedWithoutInventingZero() throws Exception {
        var charger = json.readValue("{\"connectorTypes\":[\"CCS2\"],\"maxKw\":50,\"totalConnectors\":1,\"availableConnectors\":1,\"estimatedChargeMinutes\":null}", com.navio.tripplanningservice.dto.PlannerEvChargerDto.class);
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(charger)).isEmpty();
        }
        assertThat(json.readTree(json.writeValueAsString(charger)).get("estimatedChargeMinutes").isNull()).isTrue();
    }
    private final JsonMapper json = JsonMapper.builder().build();
    @Test void omittedAndExplicitNullRemainDifferentOnTheWire() throws Exception {
        var trip = new Trip(); trip.setInitialSocPct(new BigDecimal("72.25"));
        var old = json.readValue("{\"version\":1,\"blocks\":[]}", PlannerSnapshotRequest.class);
        assertThat(old.energyState()).isNull();
        PlannerEnergyState.apply(trip, old.energyState());
        assertThat(trip.getInitialSocPct()).isEqualByComparingTo("72.25");
        var clear = json.readValue("{\"version\":1,\"blocks\":[],\"energyState\":null}", PlannerSnapshotRequest.class);
        assertThat(clear.energyState().isNull()).isTrue();
        PlannerEnergyState.apply(trip, clear.energyState());
        assertThat(trip.getInitialSocPct()).isNull();
    }
    @Test void zeroAndObservationRoundTripAndClearWithoutChangingEarlierState() throws Exception {
        var trip = new Trip(); var stop = new BlockItem();
        PlannerEnergyState.apply(trip, json.readTree("{\"initialSocPct\":0}"));
        assertThat(PlannerEnergyState.read(trip).get("initialSocPct").doubleValue()).isZero();
        var old = json.readValue("{\"id\":\"stop\",\"type\":\"place\"}", PlannerItemDto.class);
        stop.setObservedSocPct(new BigDecimal("68"));
        PlannerEnergyState.applyObservation(stop, "place", old.observedSocCheckpoint());
        assertThat(stop.getObservedSocPct()).isEqualByComparingTo("68");
        PlannerEnergyState.applyObservation(stop, "place", json.readTree("{\"socPct\":0}"));
        assertThat(PlannerEnergyState.readObservation(stop).get("socPct").doubleValue()).isZero();
        var reloaded = new BlockItem();
        PlannerEnergyState.applyObservation(reloaded, "place", json.readTree(json.writeValueAsString(PlannerEnergyState.readObservation(stop))));
        assertThat(reloaded.getObservedSocPct()).isEqualByComparingTo("0");
        PlannerEnergyState.applyObservation(stop, "place", json.nullNode());
        assertThat(stop.getObservedSocPct()).isNull();
        assertThat(trip.getInitialSocPct()).isEqualByComparingTo("0");
    }
    @Test void invalidAndNonPlaceObservationsAreRejected() throws Exception {
        for (String value : new String[]{"-1", "101", "0.001", "\"68\""}) {
            var node = json.readTree("{\"socPct\":" + value + "}");
            assertThatThrownBy(() -> PlannerEnergyState.applyObservation(new BlockItem(), "place", node))
                .isInstanceOf(PlannerService.PlannerValidationException.class);
        }
        var valid = json.readTree("{\"socPct\":68}");
        assertThatThrownBy(() -> PlannerEnergyState.applyObservation(new BlockItem(), "note", valid))
            .isInstanceOf(PlannerService.PlannerValidationException.class);
    }
    @Test void profileSnapshotRoundTripsIndependentlyAndRejectsAccountFields() throws Exception {
        var snapshot = json.readTree("""
            {"version":1,"vehicleId":"v1","label":"EV","maxAcKw":null,"maxDcKw":null,
             "connectorTypes":["CCS2"],"legacyConsumptionConfirmed":false,
             "profile":{"version":1,"modelKind":"RATED_RANGE","selectionMode":"CATALOG_DEFAULT",
             "consumptionKwhPer100km":null,"consumptionSource":"UNKNOWN","consumptionMeasurementBasis":"UNKNOWN",
             "consumptionStandard":"NONE","sourceUrl":null,"usableBatteryCapacityKwh":null,
             "capacityBasis":"MANUFACTURER_DECLARED_UNSPECIFIED","ratedRangeKm":480,"ratedRangeStandard":"NEDC"}}
            """);
        var state = json.createObjectNode().set("vehicleSnapshot", snapshot);
        var trip = new Trip(); PlannerEnergyState.apply(trip, state);
        var reloaded = new Trip(); PlannerEnergyState.apply(reloaded, json.readTree(json.writeValueAsString(PlannerEnergyState.read(trip))));
        assertThat(reloaded.getEnergyVehicleSnapshot()).isEqualTo(trip.getEnergyVehicleSnapshot());
        ((com.fasterxml.jackson.databind.node.ObjectNode) snapshot).put("accountEmail", "private@example.com");
        assertThat(PlannerEnergyState.read(trip).path("vehicleSnapshot").has("accountEmail")).isFalse();
        assertThatThrownBy(() -> PlannerEnergyState.apply(new Trip(), state)).isInstanceOf(PlannerService.PlannerValidationException.class);
    }
}
