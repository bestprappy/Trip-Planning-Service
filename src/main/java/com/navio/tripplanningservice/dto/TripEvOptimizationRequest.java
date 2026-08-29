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
        @NotNull @DecimalMin("1.0") @DecimalMax("100.0") Double startingSocPct,
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

    public record Vehicle(
            @NotNull @Positive Double batteryKwh,
            @NotNull @Positive Double consumptionKwhPer100km,
            @NotNull @PositiveOrZero Double maxAcKw,
            @NotNull @PositiveOrZero Double maxDcKw,
            @NotEmpty List<@NotBlank String> connectorTypes
    ) {
        public Vehicle {
            connectorTypes = connectorTypes == null ? null : List.copyOf(connectorTypes);
        }
    }
}
