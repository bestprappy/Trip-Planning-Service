package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.integration.mobility.MobilityEvCharger;
import com.navio.tripplanningservice.integration.mobility.MobilityEvOptimizationResponse;
import com.navio.tripplanningservice.model.BlockItem;
import com.navio.tripplanningservice.model.ListBlock;
import com.navio.tripplanningservice.model.Trip;
import com.navio.tripplanningservice.repository.BlockItemRepository;
import com.navio.tripplanningservice.repository.ListBlockRepository;
import com.navio.tripplanningservice.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripEvOptimizationApplierTest {

    @Test
    @SuppressWarnings("unchecked")
    void replacesAnUnlockedChargerAtTheServerSpecifiedInsertionPoint() {
        Fixture fixture = fixture(false);
        ArgumentCaptor<Iterable<BlockItem>> savedItems = ArgumentCaptor.forClass(Iterable.class);

        long version = fixture.applier.apply(
                fixture.tripId,
                fixture.userId,
                "day-1",
                4,
                optimization()
        );

        verify(fixture.itemRepository).delete(fixture.oldCharger);
        verify(fixture.itemRepository).saveAll(savedItems.capture());
        List<BlockItem> itinerary = StreamSupport.stream(
                savedItems.getValue().spliterator(),
                false
        ).toList();
        assertThat(itinerary).extracting(BlockItem::getClientId)
                .containsExactly("origin", itinerary.get(1).getClientId(), "destination");
        assertThat(itinerary.get(1).getStationId()).isEqualTo("better");
        assertThat(itinerary.get(1).getEvSelectionSource()).isEqualTo("AUTO");
        assertThat(itinerary.get(1).getEstimatedChargeMinutes()).isEqualTo(20);
        assertThat(version).isEqualTo(5);
    }

    @Test
    @SuppressWarnings("unchecked")
    void appendsAChargerPlannedBeforeTheDaysEndAfterTheLastSavedPlace() {
        Fixture fixture = fixture(false);
        ArgumentCaptor<Iterable<BlockItem>> savedItems = ArgumentCaptor.forClass(Iterable.class);

        fixture.applier.apply(fixture.tripId, fixture.userId, "day-1", 4, optimization("ADD_CHARGER", null, "day-1:end"));

        verify(fixture.itemRepository).saveAll(savedItems.capture());
        List<BlockItem> itinerary = StreamSupport.stream(savedItems.getValue().spliterator(), false).toList();
        assertThat(itinerary).hasSize(4);
        assertThat(itinerary.getLast().getStationId()).isEqualTo("better");
        assertThat(itinerary.get(2).getClientId()).isEqualTo("destination");
    }

    @Test
    void refusesToReplaceAChargerThatWasLockedAfterThePreview() {
        Fixture fixture = fixture(true);

        assertThatThrownBy(() -> fixture.applier.apply(
                fixture.tripId,
                fixture.userId,
                "day-1",
                4,
                optimization()
        )).isInstanceOf(TripEvOptimizationException.class)
                .hasMessageContaining("Unlock");

        verify(fixture.itemRepository, never()).delete(fixture.oldCharger);
    }

    private Fixture fixture(boolean locked) {
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        TripRepository tripRepository = mock(TripRepository.class);
        ListBlockRepository blockRepository = mock(ListBlockRepository.class);
        BlockItemRepository itemRepository = mock(BlockItemRepository.class);
        Trip trip = Trip.builder().id(tripId).userId(userId).version(4L).build();
        ListBlock block = ListBlock.builder().id(blockId).tripId(tripId).clientId("day-1").build();
        BlockItem origin = place(blockId, "origin", "Origin", 0);
        BlockItem oldCharger = place(blockId, "bad-item", "Bad charger", 1);
        oldCharger.setPlaceId("ev-charger:bad");
        oldCharger.setStationId("bad");
        oldCharger.setEvLocked(locked);
        BlockItem destination = place(blockId, "destination", "Destination", 2);

        when(tripRepository.findByIdAndUserId(tripId, userId)).thenReturn(Optional.of(trip));
        when(tripRepository.saveAndFlush(trip)).thenAnswer(invocation -> {
            trip.setVersion(5L);
            return trip;
        });
        when(blockRepository.findByTripIdAndClientId(tripId, "day-1")).thenReturn(Optional.of(block));
        when(itemRepository.findByBlockIdOrderByDisplayOrder(blockId))
                .thenReturn(List.of(origin, oldCharger, destination));

        return new Fixture(
                tripId,
                userId,
                oldCharger,
                itemRepository,
                new TripEvOptimizationApplier(tripRepository, blockRepository, itemRepository)
        );
    }

    private BlockItem place(UUID blockId, String clientId, String name, int displayOrder) {
        return BlockItem.builder()
                .blockId(blockId)
                .clientId(clientId)
                .type(BlockItem.BlockItemType.PLACE)
                .displayOrder(displayOrder)
                .title(name)
                .placeId(clientId)
                .placeName(name)
                .placeLat(13.0)
                .placeLng(100.0 + displayOrder)
                .build();
    }

    private MobilityEvOptimizationResponse optimization() {
        return optimization("REPLACE_CHARGER", "bad-item", "destination");
    }

    private MobilityEvOptimizationResponse optimization(String type, String oldItemId, String beforeItemId) {
        MobilityEvCharger charger = new MobilityEvCharger(
                "better",
                "Better charger",
                "Operator",
                new MobilityEvCharger.Location(13, 101.5, "Address", "better"),
                "Address",
                null,
                List.of("CCS2"),
                180,
                4,
                2,
                null,
                Map.of("summary", "24/7"),
                "TEST",
                "VERIFIED",
                "active",
                4.5,
                10,
                0.9,
                false
        );
        return new MobilityEvOptimizationResponse(
                "day-1",
                true,
                List.of(new MobilityEvOptimizationResponse.Operation(
                        type,
                        oldItemId,
                        beforeItemId,
                        1,
                        charger,
                        20,
                        15,
                        70,
                        2,
                        "Better charger"
                )),
                25,
                10_000,
                20,
                "Found one change.",
                List.of()
        );
    }

    private record Fixture(
            UUID tripId,
            UUID userId,
            BlockItem oldCharger,
            BlockItemRepository itemRepository,
            TripEvOptimizationApplier applier
    ) {
    }
}
