package com.navio.tripplanningservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record PlannerSnapshotRequest(
        @NotNull @PositiveOrZero Long version,
        @NotNull List<@Valid PlannerBlockDto> blocks,
        @Valid PlannerBudgetDto budget
) {
    public PlannerSnapshotRequest(Long version, List<PlannerBlockDto> blocks) {
        this(version, blocks, null);
    }
}
