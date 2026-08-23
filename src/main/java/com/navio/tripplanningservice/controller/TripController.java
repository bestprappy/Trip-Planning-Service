package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.CreateTripRequest;
import com.navio.tripplanningservice.dto.TripResponse;
import com.navio.tripplanningservice.dto.UpdateTripRequest;
import com.navio.tripplanningservice.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    public ResponseEntity<TripResponse> createTrip(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @Valid @RequestBody CreateTripRequest request) {
        TripResponse response = tripService.createTrip(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{tripId}")
    public ResponseEntity<TripResponse> getTripById(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId) {
        TripResponse response = tripService.getTripById(tripId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<TripResponse>> getUserTrips(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TripResponse> response = tripService.getUserTrips(userId, pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{tripId}")
    public ResponseEntity<TripResponse> updateTrip(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId,
            @Valid @RequestBody UpdateTripRequest request) {
        TripResponse response = tripService.updateTrip(tripId, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{tripId}")
    public ResponseEntity<Void> deleteTrip(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId) {
        tripService.deleteTrip(tripId, userId);
        return ResponseEntity.noContent().build();
    }
}
