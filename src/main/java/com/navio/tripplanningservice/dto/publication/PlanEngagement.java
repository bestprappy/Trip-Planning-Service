package com.navio.tripplanningservice.dto.publication;

/** Public aggregates only; copier identities and private copied-trip ids never leave the service. */
public record PlanEngagement(long viewCount, long copyCount, long uniqueCopierCount) { }
