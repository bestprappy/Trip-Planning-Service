package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.dto.*;
import com.navio.tripplanningservice.model.*;
import com.navio.tripplanningservice.repository.TripCoverRepository;
import com.navio.tripplanningservice.repository.TripRepository;
import com.navio.tripplanningservice.integration.mobility.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TripServiceTests {
    @Mock TripRepository repository;
    @Mock MobilityPlaceClient places;
    @Mock TripCoverRepository covers;
    @InjectMocks TripService service;
    private final UUID user = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID id = UUID.fromString("10000000-0000-4000-8000-000000000001");
    static MobilityPlaceDetail detail() {
        return new MobilityPlaceDetail("Bangkok", new MobilityPlaceDetail.Coordinates(13.7, 100.5),
                new MobilityPlaceDetail.PlaceLocation("Bangkok", "Bangkok", "TH", "Thailand"));
    }

    @Test void createsUnnamedTripFromResolvedPlace() {
        when(places.getDetail("place-id")).thenReturn(detail());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.createTrip(user, CreateTripRequest.builder().destinationId("place-id").build());
        assertThat(result.getDisplayName()).isNull();
        assertThat(result.getTitle()).isEqualTo("Thailand");
        assertThat(result.getDestinationName()).isEqualTo("Bangkok");
        assertThat(result.getDestinationCountryCode()).isEqualTo("TH");
        assertThat(result.getDestinationCountry()).isEqualTo("Thailand");
        assertThat(result.getDestinationLat()).isEqualTo(13.7);
    }

    @Test void doesNotSaveWhenResolutionFails() {
        when(places.getDetail("missing")).thenThrow(new PlaceResolutionUnavailableException("unavailable"));
        assertThatThrownBy(() -> service.createTrip(user, CreateTripRequest.builder().destinationId("missing").build()))
                .isInstanceOf(PlaceResolutionUnavailableException.class);
        verifyNoInteractions(repository);
    }

    @Test void rejectsDestinationChangeBeforeLookupWhenTripIsNotOwned() {
        when(repository.findByIdAndUserId(id, user)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateTrip(id, user, UpdateTripRequest.builder().destinationId("new").build()))
                .isInstanceOf(TripService.TripNotFoundException.class);
        verifyNoInteractions(places);
    }

    @Test void resolvesChangedDestination() {
        var trip = Trip.builder().destinationId("old").visibility(TripVisibility.PRIVATE).build();
        when(repository.findByIdAndUserId(id, user)).thenReturn(Optional.of(trip));
        when(places.getDetail("new")).thenReturn(detail());
        when(repository.save(trip)).thenReturn(trip);
        var result = service.updateTrip(id, user, UpdateTripRequest.builder().destinationId("new").build());
        assertThat(result.getDestinationCountryCode()).isEqualTo("TH");
        assertThat(result.getDestinationId()).isEqualTo("new");
    }

    @Test void renamesWithoutAnotherPlaceLookup() {
        var trip = Trip.builder().destinationId("old").destinationCity("Bangkok").visibility(TripVisibility.PRIVATE).build();
        when(repository.findByIdAndUserId(id, user)).thenReturn(Optional.of(trip));
        when(repository.save(trip)).thenReturn(trip);
        assertThat(service.updateTrip(id, user, UpdateTripRequest.builder().displayName(" Food tour ").build()).getTitle())
                .isEqualTo("Food tour");
        verifyNoInteractions(places);
    }

    @Test void titleFallsBackThroughCountryAndCityAndAllowsMissingLocation() {
        assertThat(TripResponse.builder().displayName("My trip").destinationCountry("Country").build().getTitle()).isEqualTo("My trip");
        assertThat(TripResponse.builder().destinationCity("City").destinationCountry("Country").build().getTitle()).isEqualTo("Country");
        assertThat(TripResponse.builder().destinationCity("Singapore").build().getTitle()).isEqualTo("Singapore");
        assertThat(TripResponse.builder().build().getTitle()).isNull();
    }

    @Test void tripDetailsCarryTheCurrentCoverId() {
        var cover = UUID.fromString("20000000-0000-4000-8000-000000000001");
        var trip = Trip.builder().id(id).destinationId("old").visibility(TripVisibility.PRIVATE).build();
        when(repository.findByIdAndUserId(id, user)).thenReturn(Optional.of(trip));
        when(covers.findCoverId(id)).thenReturn(Optional.of(cover));
        assertThat(service.getTripById(id, user).getCoverMediaId()).isEqualTo(cover);
    }

    @Test void tripListLooksUpCoversForTheWholePageAtOnce() {
        var other = UUID.fromString("10000000-0000-4000-8000-000000000002");
        var cover = UUID.fromString("20000000-0000-4000-8000-000000000001");
        var page = org.springframework.data.domain.PageRequest.of(0, 20);
        var first = Trip.builder().id(id).destinationId("a").visibility(TripVisibility.PRIVATE).build();
        var second = Trip.builder().id(other).destinationId("b").visibility(TripVisibility.PRIVATE).build();
        when(repository.findByUserId(user, page)).thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(first, second), page, 2));
        when(covers.findCoverIds(java.util.List.of(id, other))).thenReturn(java.util.Map.of(id, cover));
        var result = service.getUserTrips(user, page).getContent();
        assertThat(result).extracting(TripResponse::getCoverMediaId).containsExactly(cover, null);
        verify(covers, never()).findCoverId(any());
    }
}
