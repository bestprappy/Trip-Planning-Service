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
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TripEvOptimizationApplyValidationTests {
    private final UUID tripId = UUID.randomUUID(), userId = UUID.randomUUID(), blockId = UUID.randomUUID();
    private final TripRepository trips = mock(TripRepository.class);
    private final ListBlockRepository blocks = mock(ListBlockRepository.class);
    private final BlockItemRepository items = mock(BlockItemRepository.class);
    private final TripEvOptimizationApplier applier = new TripEvOptimizationApplier(trips, blocks, items);
    private final BlockItem charger = BlockItem.builder().clientId("charger").stationId("station")
            .title("Original").displayOrder(0).build();
    private final BlockItem place = BlockItem.builder().clientId("place").title("Destination").displayOrder(1).build();

    TripEvOptimizationApplyValidationTests() {
        when(trips.findByIdAndUserId(tripId, userId)).thenReturn(Optional.of(
                Trip.builder().id(tripId).userId(userId).version(4L).build()));
        when(blocks.findByTripIdAndClientId(tripId, "day")).thenReturn(Optional.of(
                ListBlock.builder().id(blockId).clientId("day").build()));
        when(items.findByBlockIdOrderByDisplayOrder(blockId)).thenReturn(List.of(charger, place));
    }

    @Test void observedCheckpointCannotBeRemoved() {
        charger.setObservedSocPct(new BigDecimal("68.25"));
        rejects(List.of(operation("REMOVE_CHARGER", "charger", null, 0)));
        assertThat(charger.getObservedSocPct()).isEqualByComparingTo("68.25");
    }

    @Test void ordinaryPlaceCannotBeRemoved() {
        rejects(List.of(operation("REMOVE_CHARGER", "place", null, 0)));
    }

    @Test void missingUpdateCannotSilentlySucceed() {
        rejects(List.of(operation("UPDATE_CHARGER", "missing", null, 5)));
    }

    @Test void updateCannotSwitchStationUnderAnObservedCheckpoint() {
        charger.setStationId("another-station");
        charger.setObservedSocPct(new BigDecimal("68.25"));
        rejects(List.of(operation("UPDATE_CHARGER", "charger", null, 5)));
    }

    @Test void validRefreshPreservesUserFieldsAndObservation() {
        charger.setObservedSocPct(new BigDecimal("28.25"));
        charger.setTargetBatteryPct(60);
        charger.setEvLocked(true);
        charger.setNotes("Keep this note");
        when(trips.saveAndFlush(any())).thenAnswer(invocation -> {
            Trip trip = invocation.getArgument(0);
            trip.setVersion(5L);
            return trip;
        });
        assertThat(applier.apply(tripId, userId, "day", 4, response("day", true,
                List.of(operation("UPDATE_CHARGER", "charger", null, 5))))).isEqualTo(5);
        assertThat(charger.getObservedSocPct()).isEqualByComparingTo("28.25");
        assertThat(charger.getTargetBatteryPct()).isEqualTo(60);
        assertThat(charger.getEvLocked()).isTrue();
        assertThat(charger.getNotes()).isEqualTo("Keep this note");
        verify(items, never()).delete(any());
    }

    @Test void preciseDurationAndOptimizerTargetAreSavedWithoutLockingTheStop() {
        when(trips.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        applier.apply(tripId, userId, "day", 4, response("day", true,
                List.of(operation("UPDATE_CHARGER", "charger", null, 5.125))));
        assertThat(charger.getChargingDurationMinutes()).isEqualTo(5.125);
        assertThat(charger.getEstimatedChargeMinutes()).isEqualTo(5);
        assertThat(charger.getPlannedDepartureSocPct()).isNotNull();
        assertThat(charger.getTargetBatteryPct()).isNull();
        assertThat(charger.getEvLocked()).isFalse();
    }

    @Test void laterInvalidOperationDoesNotDeleteEarlierCharger() {
        rejects(List.of(operation("REMOVE_CHARGER", "charger", null, 0),
                operation("ADD_CHARGER", null, "missing", 5)));
    }

    @Test void retainedChargerMovesToItsVerifiedSpanWithoutLosingItsIdentity() {
        var end = BlockItem.builder().clientId("end").title("End").displayOrder(2).build();
        when(items.findByBlockIdOrderByDisplayOrder(blockId)).thenReturn(List.of(charger, place, end));
        when(trips.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        charger.setNotes("Saved note");
        applier.apply(tripId, userId, "day", 4, response("day", true,
                List.of(operation("UPDATE_CHARGER", "charger", "end", 5.125))));
        assertThat(place.getDisplayOrder()).isZero();
        assertThat(charger.getDisplayOrder()).isEqualTo(1);
        assertThat(end.getDisplayOrder()).isEqualTo(2);
        assertThat(charger.getNotes()).isEqualTo("Saved note");
        verify(items, never()).delete(any());
    }

    @Test void insertionCannotReferenceRemovedCharger() {
        rejects(List.of(operation("REMOVE_CHARGER", "charger", null, 0),
                operation("ADD_CHARGER", null, "charger", 5)));
    }

    @Test void duplicateMutationAndUnknownOperationsAreRejected() {
        rejects(List.of(operation("REMOVE_CHARGER", "charger", null, 0),
                operation("UPDATE_CHARGER", "charger", null, 5)));
        rejects(List.of(operation("UNKNOWN", null, null, 0)));
    }

    @Test void invalidDurationsCannotBeRoundedIntoSavedData() {
        for (double minutes : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, (double) Integer.MAX_VALUE + 1}) {
            rejects(List.of(operation("ADD_CHARGER", null, "place", minutes)));
        }
    }

    @Test void infeasibleOrWrongDayResponseCannotBeApplied() {
        for (var response : List.of(response("other-day", true, List.of()), response("day", false, List.of()))) {
            assertThatThrownBy(() -> applier.apply(tripId, userId, "day", 4, response))
                    .isInstanceOf(TripEvOptimizationException.class);
        }
        noWrites();
    }

    @Test void staleVersionAndOtherUserCannotWrite() {
        assertThatThrownBy(() -> applier.apply(tripId, userId, "day", 3, response("day", true, List.of())))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThatThrownBy(() -> applier.apply(tripId, UUID.randomUUID(), "day", 4, response("day", true, List.of())))
                .isInstanceOf(TripService.TripNotFoundException.class);
        noWrites();
    }

    private void rejects(List<MobilityEvOptimizationResponse.Operation> operations) {
        assertThatThrownBy(() -> applier.apply(tripId, userId, "day", 4, response("day", true, operations)))
                .isInstanceOf(TripEvOptimizationException.class);
        noWrites();
        assertThat(charger.getTitle()).isEqualTo("Original");
        assertThat(place.getDisplayOrder()).isEqualTo(1);
    }

    private void noWrites() {
        verify(items, never()).delete(any());
        verify(items, never()).saveAll(any());
        verify(items, never()).flush();
        verify(trips, never()).saveAndFlush(any());
    }

    private MobilityEvOptimizationResponse response(String day, boolean feasible,
            List<MobilityEvOptimizationResponse.Operation> operations) {
        return new MobilityEvOptimizationResponse(day, feasible, operations, 60.0, 100, 5, "test", List.of());
    }

    private MobilityEvOptimizationResponse.Operation operation(String type, String oldId, String beforeId, double minutes) {
        var station = new MobilityEvCharger("station", "Station", "Operator",
                new MobilityEvCharger.Location(13, 100, "Address", "station"), "Address", null,
                List.of("CCS2"), 100, 2, null, null, Map.of(), "TEST", "VERIFIED", "active", 0, 0, 0, false);
        return new MobilityEvOptimizationResponse.Operation(type, oldId, beforeId, 0, station, minutes, 30, 60, 0, "test");
    }
}
