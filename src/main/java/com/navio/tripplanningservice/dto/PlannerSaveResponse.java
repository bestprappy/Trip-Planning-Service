package com.navio.tripplanningservice.dto;

import java.time.Instant;

public record PlannerSaveResponse(
        long version,
        Instant savedAt
) {
}
