package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.dto.CreateTripRequest;
import com.navio.tripplanningservice.dto.TripResponse;
import com.navio.tripplanningservice.dto.UpdateTripRequest;
import com.navio.tripplanningservice.model.Trip;
import com.navio.tripplanningservice.model.TripVisibility;
import com.navio.tripplanningservice.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {

    private final TripRepository tripRepository;
    private final com.navio.tripplanningservice.integration.mobility.MobilityPlaceClient placeClient;

    @Transactional
    public TripResponse createTrip(UUID userId, CreateTripRequest request) {
        Trip trip = Trip.builder()
                .userId(userId)
                .displayName(normalizeName(request.getDisplayName()))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .destinationId(request.getDestinationId())
                .visibility(TripVisibility.PRIVATE)
                .build();

        applyDestination(trip, placeClient.getDetail(request.getDestinationId()));
        Trip savedTrip = tripRepository.save(trip);
        return mapToResponse(savedTrip);
    }

    public TripResponse getTripById(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripNotFoundException(tripId));
        return mapToResponse(trip);
    }

    public Page<TripResponse> getUserTrips(UUID userId, Pageable pageable) {
        Page<Trip> trips = tripRepository.findByUserId(userId, pageable);
        return new PageImpl<>(
                trips.getContent().stream()
                        .map(this::mapToResponse)
                        .collect(Collectors.toList()),
                pageable,
                trips.getTotalElements()
        );
    }

    @Transactional
    public TripResponse updateTrip(UUID tripId, UUID userId, UpdateTripRequest request) {
        Trip trip = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripNotFoundException(tripId));

        if (request.getDisplayName() != null) {
            trip.setDisplayName(normalizeName(request.getDisplayName()));
        }
        if (request.getStartDate() != null) {
            trip.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            trip.setEndDate(request.getEndDate());
        }
        if (request.getDestinationId() != null && !request.getDestinationId().equals(trip.getDestinationId())) {
            var detail = placeClient.getDetail(request.getDestinationId());
            trip.setDestinationId(request.getDestinationId());
            applyDestination(trip, detail);
        }
        if (request.getVisibility() != null) {
            trip.setVisibility(TripVisibility.valueOf(request.getVisibility()));
        }

        Trip updatedTrip = tripRepository.save(trip);
        return mapToResponse(updatedTrip);
    }

    @Transactional
    public void deleteTrip(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripNotFoundException(tripId));
        tripRepository.delete(trip);
    }

    static void applyDestination(Trip trip, com.navio.tripplanningservice.integration.mobility.MobilityPlaceDetail detail) {
        trip.setDestinationName(detail.name());
        trip.setDestinationLat(detail.location().lat());
        trip.setDestinationLng(detail.location().lng());
        trip.setDestinationCity(detail.placeLocation().city());
        trip.setDestinationRegion(detail.placeLocation().region());
        trip.setDestinationCountryCode(detail.placeLocation().countryCode());
        trip.setDestinationCountry(detail.placeLocation().countryName());
    }

    private static String normalizeName(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private TripResponse mapToResponse(Trip trip) {
        return TripResponse.builder()
                .id(trip.getId())
                .displayName(trip.getDisplayName())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .destinationId(trip.getDestinationId())
                .destinationName(trip.getDestinationName())
                .destinationLat(trip.getDestinationLat())
                .destinationLng(trip.getDestinationLng())
                .destinationCountry(trip.getDestinationCountry())
                .destinationCity(trip.getDestinationCity())
                .destinationRegion(trip.getDestinationRegion())
                .destinationCountryCode(trip.getDestinationCountryCode())
                .visibility(trip.getVisibility().toString())
                .createdAt(trip.getCreatedAt())
                .updatedAt(trip.getUpdatedAt())
                .build();
    }

    public static class TripNotFoundException extends RuntimeException {
        public TripNotFoundException(UUID tripId) {
            super("Trip not found: " + tripId);
        }
    }
}
