package com.navio.tripplanningservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        String operatorName,
        @Pattern(regexp = "AUTO|MANUAL") String selectionSource,
        Boolean locked,
        @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(100) Integer targetBatteryPct
) {
    public PlannerEvChargerDto(List<String> connectorTypes, Double maxKw, Integer totalConnectors,
            Integer availableConnectors, String priceText, String openingHoursSummary,
            Integer estimatedChargeMinutes, String operatorName, String selectionSource, Boolean locked) {
        this(connectorTypes, maxKw, totalConnectors, availableConnectors, priceText,
                openingHoursSummary, estimatedChargeMinutes, operatorName, selectionSource, locked, null);
    }
    public PlannerEvChargerDto(
            List<String> connectorTypes,
            Double maxKw,
            Integer totalConnectors,
            Integer availableConnectors,
            String priceText,
            String openingHoursSummary,
            Integer estimatedChargeMinutes,
            String operatorName
    ) {
        this(
                connectorTypes,
                maxKw,
                totalConnectors,
                availableConnectors,
                priceText,
                openingHoursSummary,
                estimatedChargeMinutes,
                operatorName,
                "MANUAL",
                false
        );
    }
}
