package com.navio.tripplanningservice.dto;

import java.util.List;

/** Only aggregate counts and already-public catalogue metadata leave the service. */
public record PlaceRecommendationsResponse(List<Recommendation> items, int minimumTravelers) {
    public record Recommendation(String placeId, long travelerCount, PublicPlace place) {}
    public record PublicPlace(String name, String address, double lat, double lng, String primaryType, String imageUrl) {}
}
