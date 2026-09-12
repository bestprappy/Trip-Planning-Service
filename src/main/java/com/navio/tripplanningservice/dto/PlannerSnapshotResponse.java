package com.navio.tripplanningservice.dto;

import com.navio.tripplanningservice.model.CurrencyCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PlannerSnapshotResponse(
        List<PlannerBlockDto> blocks,
        PlannerBudgetDto budget,
        long version,
        Instant savedAt,
        List<String> capabilities
) {
    public PlannerSnapshotResponse(List<PlannerBlockDto> blocks, PlannerBudgetDto budget, long version, Instant savedAt) {
        this(blocks, budget, version, savedAt, List.of("day-destinations", "charge-targets", "day-anchors"));
    }
    public PlannerSnapshotResponse(
            List<PlannerBlockDto> blocks,
            long version,
            Instant savedAt) {
        this(
                blocks,
                new PlannerBudgetDto(CurrencyCode.THB, BigDecimal.ZERO, List.of()),
                version,
                savedAt);
    }
}
