package com.navio.tripplanningservice.dto;

import java.time.Instant;

public record PlannerSaveResponse(
        long version,
        Instant savedAt,
        java.util.List<String> capabilities
) {
    public PlannerSaveResponse(long version, Instant savedAt) {
        this(version, savedAt, java.util.List.of("day-destinations", "charge-targets", "day-anchors"));
    }
}
