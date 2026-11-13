package com.navio.tripplanningservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.model.Trip;
import com.navio.tripplanningservice.repository.PlacePopularityRepository;
import com.navio.tripplanningservice.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlacePopularityServiceTests {
    @Mock PlacePopularityRepository repository;
    @Mock TripRepository trips;
    PlacePopularityService service;
    final UUID trip=UUID.randomUUID(), user=UUID.randomUUID();
    @BeforeEach void setup() { service=new PlacePopularityService(repository,trips,new ObjectMapper(),""); }
    void owned() { when(trips.findByIdAndUserId(trip,user)).thenReturn(Optional.of(Trip.builder().id(trip).userId(user).build())); }

    @Test void anotherOwnersTripIsHiddenBeforeAnyAggregateQuery() {
        assertThatThrownBy(()->service.recommendations(trip,user,13.7,100.5)).isInstanceOf(TripService.TripNotFoundException.class);
        verifyNoInteractions(repository);
    }
    @Test void invalidCoordinatesAreRejectedBeforeAnyAggregateQuery() {
        owned();
        for(double lat:new double[]{Double.NaN,Double.POSITIVE_INFINITY,91})
            assertThatThrownBy(()->service.recommendations(trip,user,lat,100.5)).isInstanceOf(PlannerService.PlannerValidationException.class);
        assertThatThrownBy(()->service.recommendations(trip,user,13.7,181)).isInstanceOf(PlannerService.PlannerValidationException.class);
        verifyNoInteractions(repository);
    }
    @Test void countsDoNotExposePrivatePlaceMetadataAndTheTeamIsAlwaysExcluded() {
        owned();
        when(repository.nearby(eq(13.7),eq(100.5),eq(5),anyList())).thenReturn(List.of(new PlacePopularityRepository.Count("GOOGLE:a",5)));
        when(repository.publicSources(anyList(),anyInt())).thenReturn(List.of());
        var response=service.recommendations(trip,user,13.7,100.5);
        assertThat(response.items().getFirst().place()).isNull();
        verify(repository).nearby(13.7,100.5,5,List.of(UUID.fromString("0ae736e4-a315-456e-bb2e-7257d419b2d9")));
    }
    @Test void onlyPublicFieldsAreProjectedAndMalformedMetadataIsSkipped() {
        owned();
        when(repository.nearby(anyDouble(),anyDouble(),anyInt(),anyList())).thenReturn(List.of(new PlacePopularityRepository.Count("GOOGLE:a",5),new PlacePopularityRepository.Count("GOOGLE:b",6)));
        when(repository.publicSources(anyList(),anyInt())).thenReturn(List.of(
                new PlacePopularityRepository.PublicSource("GOOGLE:a","{\"name\":\"Public place\",\"lat\":13.7,\"lng\":100.5,\"notes\":\"Not returned\",\"imageUrl\":\"https://maps.googleapis.com/photo\"}"),
                new PlacePopularityRepository.PublicSource("GOOGLE:b","not json")));
        var response=service.recommendations(trip,user,13.7,100.5);
        assertThat(response.items().getFirst().place().name()).isEqualTo("Public place");
        assertThat(response.items().getFirst().place().imageUrl()).isNull();
        assertThat(response.items().get(1).place()).isNull();
    }
    @Test void preferenceIsAlwaysScopedToTheAuthenticatedAccount() {
        service.setPreference(user,false); verify(repository).setContributes(user,false);
        when(repository.contributes(user)).thenReturn(false);
        assertThat(service.preference(user).contributes()).isFalse();
    }
}
