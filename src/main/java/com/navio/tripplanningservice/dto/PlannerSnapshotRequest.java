package com.navio.tripplanningservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record PlannerSnapshotRequest(
        @NotNull @PositiveOrZero Long version,
        @NotNull List<@Valid PlannerBlockDto> blocks,
        @Valid PlannerBudgetDto budget,
        com.fasterxml.jackson.databind.JsonNode energyState
) {
    public PlannerSnapshotRequest(Long version, List<PlannerBlockDto> blocks, PlannerBudgetDto budget) {
        this(version, blocks, budget, null);
    }
    public PlannerSnapshotRequest(Long version, List<PlannerBlockDto> blocks) {
        this(version, blocks, null);
    }
}
