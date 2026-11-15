package com.navio.tripplanningservice.integration.mobility;

import java.util.List;

public record MobilityEvOptimizationResponse(
        String blockId,
        boolean feasible,
        List<Operation> operations,
        Double finalSocPct,
        long totalDrivingSeconds,
        double totalChargingMinutes,
        String message,
        List<String> warnings,
        String energyPolicyVersion
) {
    public MobilityEvOptimizationResponse(String blockId, boolean feasible, List<Operation> operations,
                                        Double finalSocPct, long totalDrivingSeconds, double totalChargingMinutes,
                                        String message, List<String> warnings) {
        this(blockId, feasible, operations, finalSocPct, totalDrivingSeconds, totalChargingMinutes, message, warnings,
                "navio-energy-v2");
    }
    public MobilityEvOptimizationResponse {
        operations = operations == null ? List.of() : List.copyOf(operations);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public record Operation(
            String type,
            String oldItemId,
            String beforeItemId,
            int sequence,
            MobilityEvCharger charger,
            double estimatedChargeMinutes,
            double arrivalSocPct,
            double departureSocPct,
            double detourKm,
            String reason
    ) {
    }
}
