package com.navio.tripplanningservice.dto;

import java.util.UUID;

/** The trip's current cover id. It changes on every upload, so clients use it as a cache key. */
public record TripCoverResponse(UUID tripId, UUID coverMediaId) {
}
