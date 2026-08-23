package com.navio.tripplanningservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record PlannerEvChargerDto(
        @NotNull List<String> connectorTypes,
        @NotNull @PositiveOrZero Double maxKw,
        @NotNull @PositiveOrZero Integer totalConnectors,
        @PositiveOrZero Integer availableConnectors,
        String priceText,
        String openingHoursSummary,
        @NotNull @PositiveOrZero Integer estimatedChargeMinutes,
        String operatorName
) {
}
