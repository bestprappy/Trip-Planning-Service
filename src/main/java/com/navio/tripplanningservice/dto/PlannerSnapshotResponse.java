package com.navio.tripplanningservice.dto;

import java.time.Instant;
import java.util.List;

public record PlannerSnapshotResponse(
        List<PlannerBlockDto> blocks,
        long version,
        Instant savedAt
) {
}
