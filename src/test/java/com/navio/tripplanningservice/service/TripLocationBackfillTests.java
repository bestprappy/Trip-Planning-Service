package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.integration.mobility.MobilityPlaceClient;
import com.navio.tripplanningservice.model.Trip;
import com.navio.tripplanningservice.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TripLocationBackfillTests {
    @Mock TripRepository repository;
    @Mock MobilityPlaceClient places;
    @Mock PlatformTransactionManager transactions;
    @Mock TripLocationBackfill.RateLimiter limiter;
    @InjectMocks TripLocationBackfill backfill;
    private Trip trip(int id, String place) {
        return Trip.builder().id(new UUID(0, id)).destinationId(place).displayName("Preserved name").build();
    }

    @Test void cachesRepeatedPlacesAcrossPagesAndSkipsResolvedTripsOnRerun() throws Exception {
        var first = trip(1, "bangkok"); var second = trip(2, "bangkok");
        when(repository.findUnresolvedAfter(new UUID(0, 0), 1)).thenReturn(List.of(first), List.of());
        when(repository.findUnresolvedAfter(first.getId(), 1)).thenReturn(List.of(second));
        when(repository.findUnresolvedAfter(second.getId(), 1)).thenReturn(List.of());
        when(repository.findById(first.getId())).thenReturn(Optional.of(first));
        when(repository.findById(second.getId())).thenReturn(Optional.of(second));
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(places.getDetail("bangkok")).thenReturn(TripServiceTests.detail());
        assertThat(backfill.run(1, 10, limiter)).isEqualTo(2);
        assertThat(backfill.run(1, 10, limiter)).isZero();
        verify(places).getDetail("bangkok"); verify(limiter).await();
        assertThat(first.getDisplayName()).isEqualTo("Preserved name");
        assertThat(second.getDestinationCountryCode()).isEqualTo("TH");
    }

    @Test void advancesPastUnresolvablePlacesAndRetriesThemOnTheNextRun() throws Exception {
        var missing = trip(1, "missing"); var guest = trip(2, "guest-1");
        when(repository.findUnresolvedAfter(new UUID(0, 0), 10)).thenReturn(List.of(missing, guest));
        when(repository.findUnresolvedAfter(guest.getId(), 8)).thenReturn(List.of());
        when(places.getDetail("missing")).thenThrow(new PlaceResolutionUnavailableException("gone"));
        assertThat(backfill.run(10, 10, limiter)).isZero();
        assertThat(backfill.run(10, 10, limiter)).isZero();
        verify(places, times(2)).getDetail("missing");
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(transactions);
    }

    @Test void doesNotOverwriteAChangedDestination() throws Exception {
        var candidate = trip(1, "old"); var current = trip(1, "new");
        when(repository.findUnresolvedAfter(new UUID(0, 0), 1)).thenReturn(List.of(candidate));
        when(repository.findById(candidate.getId())).thenReturn(Optional.of(current));
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(places.getDetail("old")).thenReturn(TripServiceTests.detail());
        assertThat(backfill.run(1, 1, limiter)).isZero();
        verify(repository, never()).saveAndFlush(any());
    }
}
