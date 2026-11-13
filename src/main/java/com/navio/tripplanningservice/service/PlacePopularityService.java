package com.navio.tripplanningservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.dto.PlacePopularityPreference;
import com.navio.tripplanningservice.dto.PlaceRecommendationsResponse;
import com.navio.tripplanningservice.dto.PlaceRecommendationsResponse.PublicPlace;
import com.navio.tripplanningservice.dto.PlaceRecommendationsResponse.Recommendation;
import com.navio.tripplanningservice.repository.PlacePopularityRepository;
import com.navio.tripplanningservice.repository.TripRepository;
import com.navio.tripplanningservice.service.publication.PlanPublicationSanitizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PlacePopularityService {
    public static final int MINIMUM_TRAVELERS = 5;
    private final PlacePopularityRepository popularity;
    private final TripRepository trips;
    private final ObjectMapper json;
    private final List<UUID> excluded;

    public PlacePopularityService(PlacePopularityRepository popularity, TripRepository trips, ObjectMapper json,
            @Value("${navio.place-popularity.excluded-accounts:0ae736e4-a315-456e-bb2e-7257d419b2d9}") String excludedAccounts) {
        this.popularity = popularity;
        this.trips = trips;
        this.json = json;
        // The dedicated sample owner is always excluded; configuration can add test/service accounts.
        this.excluded = java.util.stream.Stream.concat(
                java.util.stream.Stream.of("0ae736e4-a315-456e-bb2e-7257d419b2d9"),
                Arrays.stream(excludedAccounts.split(",")).map(String::trim).filter(s -> !s.isEmpty()))
                .map(UUID::fromString).distinct().toList();
    }

    public PlaceRecommendationsResponse recommendations(UUID tripId, UUID user, double lat, double lng) {
        trips.findByIdAndUserId(tripId, user).orElseThrow(() -> new TripService.TripNotFoundException(tripId));
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
            throw new PlannerService.PlannerValidationException("Choose a valid destination");
        }
        var counts = popularity.nearby(lat, lng, MINIMUM_TRAVELERS, excluded);
        var sources = new HashMap<String, PublicPlace>();
        for (var source : popularity.publicSources(counts.stream().map(PlacePopularityRepository.Count::placeId).toList(),
                PlanPublicationSanitizer.SANITIZER_VERSION)) {
            try {
                var node = json.readTree(source.json());
                if (!node.path("name").isTextual() || node.path("name").asText().isBlank()
                        || !node.path("lat").isNumber() || !node.path("lng").isNumber()) continue;
                double placeLat = node.path("lat").asDouble(), placeLng = node.path("lng").asDouble();
                if (!Double.isFinite(placeLat) || !Double.isFinite(placeLng) || Math.abs(placeLat) > 90 || Math.abs(placeLng) > 180) continue;
                sources.put(source.placeId(), new PublicPlace(node.path("name").asText(), node.path("address").asText(""),
                        placeLat, placeLng, node.path("placeType").asText(null), null));
            } catch (Exception ignored) {
                // A malformed old public snapshot must not prevent valid guide recommendations.
            }
        }
        return new PlaceRecommendationsResponse(counts.stream()
                .map(count -> new Recommendation(count.placeId(), count.travelers(), sources.get(count.placeId()))).toList(),
                MINIMUM_TRAVELERS);
    }

    public PlacePopularityPreference preference(UUID user) {
        return new PlacePopularityPreference(popularity.contributes(user));
    }

    @Transactional
    public PlacePopularityPreference setPreference(UUID user, boolean contributes) {
        popularity.setContributes(user, contributes);
        return new PlacePopularityPreference(contributes);
    }
}
