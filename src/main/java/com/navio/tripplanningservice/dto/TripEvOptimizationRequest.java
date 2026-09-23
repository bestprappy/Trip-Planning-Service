package com.navio.tripplanningservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TripEvOptimizationRequest(
        @NotBlank @Size(max = 160) String blockId,
        @NotNull @Valid Vehicle vehicle,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double startingSocPct,
        @DecimalMin("1.0") @DecimalMax("40.0") Double reserveSocPct,
        @DecimalMin("20.0") @DecimalMax("95.0") Double targetSocPct,
        @DecimalMin("1.0") @DecimalMax("50.0") Double maximumDetourKm,
        @PositiveOrZero Long expectedVersion
) {
    public double effectiveReserveSocPct() {
        return reserveSocPct == null ? 12 : reserveSocPct;
    }

    public double effectiveTargetSocPct() {
        return targetSocPct == null ? 70 : targetSocPct;
    }

    public double effectiveMaximumDetourKm() {
        return maximumDetourKm == null ? 20 : maximumDetourKm;
    }

    public record EnergyModel(
            @NotBlank @jakarta.validation.constraints.Pattern(regexp="CONSUMPTION|RATED_RANGE|UNAVAILABLE") String modelKind,
            @Positive Double consumptionKwhPer100km, @Positive Double usableBatteryCapacityKwh, @Positive Double ratedRangeKm) {}

    public record Vehicle(
            @PositiveOrZero Double batteryKwh,
            @PositiveOrZero Double consumptionKwhPer100km,
            @PositiveOrZero Double maxAcKw,
            @PositiveOrZero Double maxDcKw,
            @NotEmpty List<@NotBlank String> connectorTypes,
            @Valid EnergyModel energyModel
    ) {
        public Vehicle(Double batteryKwh, Double consumptionKwhPer100km, Double maxAcKw, Double maxDcKw, List<String> connectorTypes) {
            this(batteryKwh, consumptionKwhPer100km, maxAcKw, maxDcKw, connectorTypes, null);
        }
        public Vehicle {
            connectorTypes = connectorTypes == null ? null : List.copyOf(connectorTypes);
        }
    }
}
