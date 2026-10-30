package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.dto.AppendMyListPlaceRequest;
import com.navio.tripplanningservice.integration.mobility.*;
import com.navio.tripplanningservice.model.*;
import com.navio.tripplanningservice.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MyListPlaceServiceTests {
    @Mock TripRepository trips;
    @Mock ListBlockRepository blocks;
    @Mock BlockItemRepository items;
    @Mock MobilityPlaceClient places;
    @InjectMocks MyListPlaceService service;
    final UUID tripId = UUID.randomUUID(), userId = UUID.randomUUID(), blockId = UUID.randomUUID();
    final Trip trip = Trip.builder().id(tripId).userId(userId).version(4L)
            .startDate(LocalDate.of(2026, 12, 1)).displayName("Preserve me").build();
    AppendMyListPlaceRequest request(String list, long version) {
        return new AppendMyListPlaceRequest("google-1", "GOOGLE", list, version);
    }
    ListBlock list(String id) {
        return ListBlock.builder().id(blockId).clientId(id).type(ListBlock.ListBlockType.LIST).displayOrder(7).build();
    }
    void owned(List<ListBlock> list) {
        when(trips.findOwnedForUpdate(tripId, userId)).thenReturn(Optional.of(trip));
        when(blocks.findByTripIdOrderByDisplayOrder(tripId)).thenReturn(list);
    }
    void detail() {
        when(places.getDetail("google-1")).thenReturn(new MobilityPlaceDetail("Garden",
                new MobilityPlaceDetail.Coordinates(13.7, 100.5), null));
        when(trips.saveAndFlush(trip)).thenAnswer(call -> { trip.setVersion(5L); return trip; });
    }
    @Test void ownershipIsCheckedBeforeProviderOrListReads() {
        when(trips.findOwnedForUpdate(tripId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.append(tripId, userId, request(null, 4)))
                .isInstanceOf(TripService.TripNotFoundException.class);
        verifyNoInteractions(blocks, items, places);
    }
    @Test void createsListWithoutRewritingPlannerMetadata() {
        owned(List.of()); detail();
        when(blocks.save(any())).thenAnswer(call -> { ListBlock b = call.getArgument(0); b.setId(blockId); return b; });
        var result = service.append(tripId, userId, request(null, 4));
        assertThat(result.alreadyAdded()).isFalse();
        assertThat(result.version()).isEqualTo(5);
        ArgumentCaptor<ListBlock> block = ArgumentCaptor.forClass(ListBlock.class);
        verify(blocks).save(block.capture());
        assertThat(block.getValue().getName()).isEqualTo("My List");
        assertThat(block.getValue().getBlockDate()).isEqualTo(trip.getStartDate());
        assertThat(block.getValue().getBlockColor()).isEqualTo("teal");
        assertThat(trip.getDisplayName()).isEqualTo("Preserve me");
        ArgumentCaptor<BlockItem> item = ArgumentCaptor.forClass(BlockItem.class);
        verify(items).save(item.capture());
        assertThat(item.getValue().getPlaceId()).isEqualTo("google-1");
        assertThat(item.getValue().getPlaceLat()).isEqualTo(13.7);
        assertThat(item.getValue().getPlaceAddress()).isEmpty();
        verify(blocks, never()).delete(any());
        verify(items, never()).delete(any());
    }
    @Test void duplicatesAreIdempotentEvenAfterVersionHasAdvanced() {
        owned(List.of(list("list-1")));
        when(items.findByBlockIdOrderByDisplayOrder(blockId)).thenReturn(List.of(BlockItem.builder()
                .clientId("existing").type(BlockItem.BlockItemType.PLACE).placeId("google-1").build()));
        var result = service.append(tripId, userId, request("list-1", 1));
        assertThat(result.alreadyAdded()).isTrue();
        assertThat(result.itemId()).isEqualTo("existing");
        verifyNoInteractions(places);
        verify(items, never()).save(any());
        verify(trips, never()).saveAndFlush(any());
    }
    @Test void nonduplicateStaleWriteConflictsBeforeProviderRequest() {
        owned(List.of());
        assertThatThrownBy(() -> service.append(tripId, userId, request(null, 3)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verifyNoInteractions(places, items);
    }
    @Test void severalListsRequireExplicitChoice() {
        owned(List.of(list("one"), list("two")));
        assertThatThrownBy(() -> service.append(tripId, userId, request(null, 4)))
                .isInstanceOf(PlannerService.PlannerValidationException.class);
        verifyNoInteractions(places, items);
    }
    @Test void cannotAppendIntoAnotherTripsListOrItinerary() {
        owned(List.of(ListBlock.builder().clientId("day").type(ListBlock.ListBlockType.ITINERARY).build()));
        assertThatThrownBy(() -> service.append(tripId, userId, request("day", 4)))
                .isInstanceOf(PlannerService.PlannerValidationException.class);
        verifyNoInteractions(places, items);
    }
    @Test void unavailablePlaceDoesNotCreateEmptyList() {
        owned(List.of());
        when(places.getDetail("google-1")).thenThrow(new PlaceResolutionUnavailableException("Unavailable"));
        assertThatThrownBy(() -> service.append(tripId, userId, request(null, 4)))
                .isInstanceOf(PlaceResolutionUnavailableException.class);
        verify(blocks, never()).save(any());
        verifyNoInteractions(items);
    }
    @Test void invalidCoordinatesDoNotWrite() {
        owned(List.of());
        when(places.getDetail("google-1")).thenReturn(new MobilityPlaceDetail("Garden",
                new MobilityPlaceDetail.Coordinates(Double.NaN, 100.5), null));
        assertThatThrownBy(() -> service.append(tripId, userId, request(null, 4)))
                .isInstanceOf(PlannerService.PlannerValidationException.class);
        verify(blocks, never()).save(any());
        verifyNoInteractions(items);
    }
    @Test void savedCoordinateLocationIsReusableWithoutGoogleResolution() {
        owned(List.of(list("list-1")));
        when(trips.saveAndFlush(trip)).thenReturn(trip);
        var request=new AppendMyListPlaceRequest("MAPBOX:place-one","SAVED_LOCATION","list-1",4L,
            new AppendMyListPlaceRequest.SavedLocation("Saved cafe","Saved address",13.0,100.0));
        service.append(tripId,userId,request);
        var captured=ArgumentCaptor.forClass(BlockItem.class);verify(items).save(captured.capture());
        assertThat(captured.getValue().getPlaceLat()).isEqualTo(13.0);
        assertThat(captured.getValue().getPlaceAddress()).isEqualTo("Saved address");
        verifyNoInteractions(places);
    }
}
