package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.dto.PlannerSnapshotResponse;
import com.navio.tripplanningservice.dto.TripEvOptimizationRequest;
import com.navio.tripplanningservice.integration.mobility.MobilityEvCharger;
import com.navio.tripplanningservice.integration.mobility.MobilityEvOptimizationClient;
import com.navio.tripplanningservice.integration.mobility.MobilityEvOptimizationResponse;
import com.navio.tripplanningservice.model.BlockItem;
import com.navio.tripplanningservice.model.ListBlock;
import com.navio.tripplanningservice.model.Trip;
import com.navio.tripplanningservice.repository.BlockItemRepository;
import com.navio.tripplanningservice.repository.ListBlockRepository;
import com.navio.tripplanningservice.repository.TripRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripEvOptimizationServiceTest {

    @Test
    void loadsTheOwnedTripAndSendsASelfContainedSnapshotToMobility() {
        Fixture fixture = fixture();
        when(fixture.mobilityClient.optimize(any())).thenReturn(mobilityResponse());

        var preview = fixture.service.preview(fixture.tripId, fixture.userId, request(null));

        assertThat(preview.baseVersion()).isEqualTo(4);
        assertThat(preview.operations()).hasSize(1);
        assertThat(preview.operations().getFirst().type()).isEqualTo("REPLACE_CHARGER");
        verify(fixture.mobilityClient).optimize(any());
    }

    @Test
    void revalidatesAndAppliesTheOptimizationAtThePreviewVersion() {
        Fixture fixture = fixture();
        when(fixture.mobilityClient.optimize(any())).thenReturn(mobilityResponse());
        PlannerSnapshotResponse snapshot = new PlannerSnapshotResponse(List.of(), 5, Instant.now());
        when(fixture.plannerService.getPlannerSnapshot(fixture.tripId, fixture.userId)).thenReturn(snapshot);

        PlannerSnapshotResponse response = fixture.service.apply(
                fixture.tripId,
                fixture.userId,
                request(4L)
        );

        assertThat(response.version()).isEqualTo(5);
        verify(fixture.applier).apply(
                fixture.tripId,
                fixture.userId,
                "day-1",
                4L,
                mobilityResponse()
        );
    }

    private Fixture fixture() {
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        TripRepository tripRepository = mock(TripRepository.class);
        ListBlockRepository listBlockRepository = mock(ListBlockRepository.class);
        BlockItemRepository itemRepository = mock(BlockItemRepository.class);
        MobilityEvOptimizationClient mobilityClient = mock(MobilityEvOptimizationClient.class);
        TripEvOptimizationApplier applier = mock(TripEvOptimizationApplier.class);
        PlannerService plannerService = mock(PlannerService.class);
        Trip trip = Trip.builder().id(tripId).userId(userId).version(4L).build();
        ListBlock block = ListBlock.builder().id(blockId).tripId(tripId).clientId("day-1").build();
        when(tripRepository.findByIdAndUserId(tripId, userId)).thenReturn(Optional.of(trip));
        when(listBlockRepository.findByTripIdAndClientId(tripId, "day-1")).thenReturn(Optional.of(block));
        when(itemRepository.findByBlockIdOrderByDisplayOrder(blockId)).thenReturn(List.of(
                place("origin", "Origin", 13, 100),
                chargerItem("bad-item", "bad", 13, 101),
                place("destination", "Destination", 13, 103)
        ));
        TripEvOptimizationService service = new TripEvOptimizationService(
                tripRepository,
                listBlockRepository,
                itemRepository,
                mobilityClient,
                applier,
                plannerService
        );
        return new Fixture(
                tripId,
                userId,
                mobilityClient,
                applier,
                plannerService,
                service
        );
    }

    private TripEvOptimizationRequest request(Long expectedVersion) {
        return new TripEvOptimizationRequest(
                "day-1",
                new TripEvOptimizationRequest.Vehicle(
                        75.0,
                        18.0,
                        11.0,
                        180.0,
                        List.of("CCS2")
                ),
                80.0,
                12.0,
                70.0,
                20.0,
                expectedVersion
        );
    }

    private MobilityEvOptimizationResponse mobilityResponse() {
        return new MobilityEvOptimizationResponse(
                "day-1",
                true,
                List.of(new MobilityEvOptimizationResponse.Operation(
                        "REPLACE_CHARGER",
                        "bad-item",
                        "destination",
                        1,
                        mobilityCharger("better"),
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

    private MobilityEvCharger mobilityCharger(String id) {
        return new MobilityEvCharger(
                id,
                id,
                "Operator",
                new MobilityEvCharger.Location(13, 101.5, "Address", id),
                "Address",
                null,
                List.of("CCS2"),
                180,
                4,
                2,
                null,
                Map.of(),
                "TEST",
                "VERIFIED",
                "active",
                4.5,
                10,
                0.9,
                false
        );
    }

    private BlockItem place(String clientId, String name, double lat, double lng) {
        return BlockItem.builder()
                .clientId(clientId)
                .type(BlockItem.BlockItemType.PLACE)
                .title(name)
                .placeId(clientId)
                .placeName(name)
                .placeLat(lat)
                .placeLng(lng)
                .build();
    }

    private BlockItem chargerItem(String clientId, String chargerId, double lat, double lng) {
        BlockItem item = place(clientId, "Bad charger", lat, lng);
        item.setPlaceId("ev-charger:" + chargerId);
        item.setStationId(chargerId);
        item.setEvConnectorTypes("CCS2");
        item.setPowerKw(50.0);
        item.setEvTotalConnectors(2);
        item.setEvAvailableConnectors(0);
        item.setEvSelectionSource("MANUAL");
        item.setEvLocked(false);
        return item;
    }

    private record Fixture(
            UUID tripId,
            UUID userId,
            MobilityEvOptimizationClient mobilityClient,
            TripEvOptimizationApplier applier,
            PlannerService plannerService,
            TripEvOptimizationService service
    ) {
    }
}
