package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.PlacePopularityPreference;
import com.navio.tripplanningservice.dto.PlaceRecommendationsResponse;
import com.navio.tripplanningservice.service.PlacePopularityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/trips")
@RequiredArgsConstructor
public class PlacePopularityController {
    private final PlacePopularityService service;

    @GetMapping("/{tripId}/place-recommendations")
    public ResponseEntity<PlaceRecommendationsResponse> recommendations(
            @RequestHeader("X-User-Id") UUID user, @PathVariable UUID tripId,
            @RequestParam double lat, @RequestParam double lng) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.recommendations(tripId, user, lat, lng));
    }

    @GetMapping("/place-popularity/preferences")
    public ResponseEntity<PlacePopularityPreference> preference(@RequestHeader("X-User-Id") UUID user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.preference(user));
    }

    @PutMapping("/place-popularity/preferences")
    public ResponseEntity<PlacePopularityPreference> setPreference(@RequestHeader("X-User-Id") UUID user,
            @Valid @RequestBody PlacePopularityPreference request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.setPreference(user, request.contributes()));
    }
}
