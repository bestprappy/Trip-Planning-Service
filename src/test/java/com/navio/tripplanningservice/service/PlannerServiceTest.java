package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.dto.PlannerAnchorDto;
import com.navio.tripplanningservice.dto.PlannerBlockDto;
import com.navio.tripplanningservice.dto.PlannerDestinationDto;
import com.navio.tripplanningservice.dto.PlannerBudgetDto;
import com.navio.tripplanningservice.dto.PlannerChecklistSubItemDto;
import com.navio.tripplanningservice.dto.PlannerEvChargerDto;
import com.navio.tripplanningservice.dto.PlannerExpenseDto;
import com.navio.tripplanningservice.dto.PlannerItemDto;
import com.navio.tripplanningservice.dto.PlannerSaveResponse;
import com.navio.tripplanningservice.dto.PlannerSnapshotRequest;
import com.navio.tripplanningservice.model.BlockItem;
import com.navio.tripplanningservice.model.ChecklistSubItem;
import com.navio.tripplanningservice.model.CurrencyCode;
import com.navio.tripplanningservice.model.Expense;
import com.navio.tripplanningservice.model.ExpenseCategory;
import com.navio.tripplanningservice.model.ListBlock;
import com.navio.tripplanningservice.model.Trip;
import com.navio.tripplanningservice.repository.BlockItemRepository;
import com.navio.tripplanningservice.repository.ChecklistSubItemRepository;
import com.navio.tripplanningservice.repository.ExpenseRepository;
import com.navio.tripplanningservice.repository.ListBlockRepository;
import com.navio.tripplanningservice.repository.TripRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TRIP_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID BLOCK_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID ITEM_ID = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID SUB_ITEM_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");

    @Mock
    private TripRepository tripRepository;

    @Mock
    private ListBlockRepository listBlockRepository;

    @Mock
    private BlockItemRepository blockItemRepository;

    @Mock
    private ChecklistSubItemRepository checklistSubItemRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    private PlannerService plannerService;

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        plannerService = new PlannerService(
                tripRepository,
                listBlockRepository,
                blockItemRepository,
                checklistSubItemRepository,
                expenseRepository,
                meterRegistry);
    }

    @Test
    void savesPlannerAndReturnsACompactAcknowledgement() {
        Trip trip = ownedTrip(3L);
        ListBlock savedBlock = ListBlock.builder().id(BLOCK_ID).tripId(TRIP_ID).build();
        BlockItem savedItem = BlockItem.builder().id(ITEM_ID).blockId(BLOCK_ID).build();
        ChecklistSubItem savedSubItem = ChecklistSubItem.builder()
                .id(SUB_ITEM_ID)
                .itemId(ITEM_ID)
                .build();

        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID))
                .thenReturn(Optional.of(trip));
        when(listBlockRepository.findByTripIdOrderByDisplayOrder(TRIP_ID))
                .thenReturn(List.of(), List.of(savedBlock));
        when(listBlockRepository.save(any(ListBlock.class))).thenAnswer(invocation -> {
            ListBlock block = invocation.getArgument(0);
            block.setId(BLOCK_ID);
            savedBlock.setClientId(block.getClientId());
            savedBlock.setName(block.getName());
            savedBlock.setType(block.getType());
            savedBlock.setDisplayOrder(block.getDisplayOrder());
            savedBlock.setBlockColor(block.getBlockColor());
            savedBlock.setBlockDate(block.getBlockDate());
            return savedBlock;
        });
        when(blockItemRepository.findByBlockIdOrderByDisplayOrder(BLOCK_ID))
                .thenReturn(List.of(), List.of(savedItem));
        when(blockItemRepository.save(any(BlockItem.class))).thenAnswer(invocation -> {
            BlockItem item = invocation.getArgument(0);
            item.setId(ITEM_ID);
            savedItem.setClientId(item.getClientId());
            savedItem.setType(item.getType());
            savedItem.setDisplayOrder(item.getDisplayOrder());
            savedItem.setTitle(item.getTitle());
            return savedItem;
        });
        when(checklistSubItemRepository.findByItemIdOrderByDisplayOrder(ITEM_ID))
                .thenReturn(List.of(), List.of(savedSubItem));
        when(checklistSubItemRepository.save(any(ChecklistSubItem.class))).thenAnswer(invocation -> {
            ChecklistSubItem subItem = invocation.getArgument(0);
            subItem.setId(SUB_ITEM_ID);
            savedSubItem.setClientId(subItem.getClientId());
            savedSubItem.setLabel(subItem.getLabel());
            savedSubItem.setChecked(subItem.getChecked());
            savedSubItem.setDisplayOrder(subItem.getDisplayOrder());
            return savedSubItem;
        });
        when(tripRepository.saveAndFlush(trip)).thenAnswer(invocation -> {
            trip.setVersion(4L);
            return trip;
        });

        PlannerSaveResponse response = plannerService.savePlannerSnapshot(
                TRIP_ID,
                USER_ID,
                checklistRequest(3L));

        assertEquals(4L, response.version());
        assertEquals(trip.getUpdatedAt(), response.savedAt());
        assertEquals(
                1L,
                meterRegistry.get("navio.planner.autosave")
                        .tag("outcome", "success")
                        .timer()
                        .count());
    }

    @Test
    void rejectsAStaleSnapshotBeforeWritingChildren() {
        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID))
                .thenReturn(Optional.of(ownedTrip(5L)));

        assertThrows(
                ObjectOptimisticLockingFailureException.class,
                () -> plannerService.savePlannerSnapshot(
                        TRIP_ID,
                        USER_ID,
                        checklistRequest(4L)));

        verify(listBlockRepository, never()).save(any(ListBlock.class));
        assertEquals(
                1L,
                meterRegistry.get("navio.planner.autosave")
                        .tag("outcome", "failure")
                        .timer()
                        .count());
    }

    @Test
    void deletesBlocksAndTheirChildrenWhenOmittedFromSnapshot() {
        Trip trip = ownedTrip(1L);
        ListBlock block = ListBlock.builder().id(BLOCK_ID).tripId(TRIP_ID).build();
        BlockItem item = BlockItem.builder().id(ITEM_ID).blockId(BLOCK_ID).build();

        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID))
                .thenReturn(Optional.of(trip));
        when(listBlockRepository.findByTripIdOrderByDisplayOrder(TRIP_ID))
                .thenReturn(List.of(block), List.of());
        when(blockItemRepository.findByBlockIdOrderByDisplayOrder(BLOCK_ID))
                .thenReturn(List.of(item));
        when(tripRepository.saveAndFlush(trip)).thenAnswer(invocation -> {
            trip.setVersion(2L);
            return trip;
        });

        PlannerSaveResponse response = plannerService.savePlannerSnapshot(
                TRIP_ID,
                USER_ID,
                new PlannerSnapshotRequest(1L, List.of()));

        assertEquals(2L, response.version());
        verify(checklistSubItemRepository).deleteByItemId(ITEM_ID);
        verify(blockItemRepository).delete(item);
        verify(listBlockRepository).delete(block);
    }

    @Test
    void savesBudgetSettingsAndExpensesWithThePlannerSnapshot() {
        Trip trip = ownedTrip(2L);
        PlannerExpenseDto expense = new PlannerExpenseDto(
                "expense-dinner",
                new BigDecimal("850.00"),
                "Dinner",
                "food",
                LocalDate.of(2026, 9, 2));
        PlannerBudgetDto budget = new PlannerBudgetDto(
                CurrencyCode.USD,
                new BigDecimal("2500.00"),
                List.of(expense));

        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID))
                .thenReturn(Optional.of(trip));
        when(listBlockRepository.findByTripIdOrderByDisplayOrder(TRIP_ID))
                .thenReturn(List.of());
        when(expenseRepository.findByTripIdOrderByDisplayOrder(TRIP_ID))
                .thenReturn(List.of());
        when(expenseRepository.save(any(Expense.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tripRepository.saveAndFlush(trip)).thenAnswer(invocation -> {
            trip.setVersion(3L);
            return trip;
        });

        PlannerSaveResponse response = plannerService.savePlannerSnapshot(
                TRIP_ID,
                USER_ID,
                new PlannerSnapshotRequest(2L, List.of(), budget));

        assertEquals(3L, response.version());
        assertEquals(CurrencyCode.USD, trip.getBudgetCurrency());
        assertEquals(new BigDecimal("2500.00"), trip.getBudgetAmount());
        verify(expenseRepository).save(org.mockito.ArgumentMatchers.argThat(savedExpense ->
                savedExpense.getClientId().equals("expense-dinner")
                        && savedExpense.getCategory() == ExpenseCategory.FOOD
                        && savedExpense.getAmount().compareTo(new BigDecimal("850.00")) == 0));
    }

    private Trip ownedTrip(long version) {
        return Trip.builder()
                .id(TRIP_ID)
                .userId(USER_ID)
                .version(version)
                .build();
    }

    @Test
    void savesTheFullWeekdayOpeningHoursSummaryFromTheProvider() {
        // Google Places returns opening hours as seven weekday descriptions joined together. At
        // ~165 characters that overflowed the old VARCHAR(100) column and failed the whole autosave
        // with a constraint violation, which surfaced as "an unexpected error occurred".
        String weekdayHours = String.join(" | ", java.util.Collections.nCopies(7, "Monday: Open 24 hours"));
        assertEquals(165, weekdayHours.length());

        assertEquals(weekdayHours, savedOpeningHours(weekdayHours));
    }

    @Test
    void clampsChargerTextThatWouldStillOverflowItsColumn() {
        assertEquals("x".repeat(255), savedOpeningHours("x".repeat(400)));
    }

    @Test
    void roundTripsDayDestinationsAndChargeTargetsWithoutRemovingStops() {
        Trip trip = ownedTrip(1L);
        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).thenReturn(Optional.of(trip));
        when(listBlockRepository.findByTripIdOrderByDisplayOrder(TRIP_ID)).thenReturn(List.of());
        when(listBlockRepository.save(any(ListBlock.class))).thenAnswer(call -> {
            ListBlock block = call.getArgument(0); block.setId(BLOCK_ID); return block;
        });
        when(blockItemRepository.findByBlockIdOrderByDisplayOrder(BLOCK_ID)).thenReturn(List.of());
        when(blockItemRepository.save(any(BlockItem.class))).thenAnswer(call -> {
            BlockItem item = call.getArgument(0); item.setId(ITEM_ID); return item;
        });
        when(tripRepository.saveAndFlush(trip)).thenReturn(trip);
        PlannerBlockDto original = chargerRequest(1L, "Open 24 hours", 80).blocks().getFirst();
        PlannerDestinationDto destination = new PlannerDestinationDto("bangkok", "Bangkok", 13.75, 100.5, "Thailand");
        PlannerBlockDto changed = new PlannerBlockDto(original.id(), "itinerary", original.title(), original.date(), original.colorId(), original.items(), destination);
        PlannerSaveResponse ack = plannerService.savePlannerSnapshot(TRIP_ID, USER_ID, new PlannerSnapshotRequest(1L, List.of(changed)));
        ArgumentCaptor<ListBlock> block = ArgumentCaptor.forClass(ListBlock.class);
        ArgumentCaptor<BlockItem> item = ArgumentCaptor.forClass(BlockItem.class);
        verify(listBlockRepository).save(block.capture());
        verify(blockItemRepository).save(item.capture());
        assertEquals("Bangkok", block.getValue().getDestinationName());
        assertEquals(80, item.getValue().getTargetBatteryPct());
        assertEquals(List.of("day-destinations", "charge-targets", "day-anchors"), ack.capabilities());
        when(listBlockRepository.findByTripIdOrderByDisplayOrder(TRIP_ID)).thenReturn(List.of(block.getValue()));
        when(blockItemRepository.findByBlockIdOrderByDisplayOrder(BLOCK_ID)).thenReturn(List.of(item.getValue()));
        var restored = plannerService.getPlannerSnapshot(TRIP_ID, USER_ID).blocks().getFirst();
        assertEquals(destination, restored.destination());
        assertEquals(1, restored.items().size());
        assertEquals(80, restored.items().getFirst().evCharger().targetBatteryPct());
    }

    @Test
    void roundTripsDayStartAndEndAnchorsIncludingTheirKind() {
        Trip trip = ownedTrip(1L);
        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).thenReturn(Optional.of(trip));
        when(listBlockRepository.findByTripIdOrderByDisplayOrder(TRIP_ID)).thenReturn(List.of());
        when(listBlockRepository.save(any(ListBlock.class))).thenAnswer(call -> {
            ListBlock block = call.getArgument(0); block.setId(BLOCK_ID); return block;
        });
        when(blockItemRepository.findByBlockIdOrderByDisplayOrder(BLOCK_ID)).thenReturn(List.of());
        when(tripRepository.saveAndFlush(trip)).thenReturn(trip);

        PlannerAnchorDto home = new PlannerAnchorDto(
                "20000000-0000-4000-8000-000000000009", "SAVED_PLACE", "Home", "Bangkok", 13.75, 100.5);
        PlannerAnchorDto hotel = new PlannerAnchorDto(
                "places/hotel-1", "PLACE", "Hua Hin Resort", "Hua Hin", 12.57, 99.95);

        plannerService.savePlannerSnapshot(TRIP_ID, USER_ID,
                new PlannerSnapshotRequest(1L, List.of(anchoredBlock(home, hotel))));

        ArgumentCaptor<ListBlock> block = ArgumentCaptor.forClass(ListBlock.class);
        verify(listBlockRepository).save(block.capture());
        assertEquals("SAVED_PLACE", block.getValue().getStartAnchorKind());
        assertEquals("Hua Hin Resort", block.getValue().getEndAnchorName());

        when(listBlockRepository.findByTripIdOrderByDisplayOrder(TRIP_ID))
                .thenReturn(List.of(block.getValue()));
        var restored = plannerService.getPlannerSnapshot(TRIP_ID, USER_ID).blocks().getFirst();
        assertEquals(home, restored.startAnchor());
        assertEquals(hotel, restored.endAnchor());
    }

    @Test
    void aDayWithNoAnchorsStoresNothingSoItsStartStaysDerived() {
        Trip trip = ownedTrip(1L);
        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).thenReturn(Optional.of(trip));
        when(listBlockRepository.findByTripIdOrderByDisplayOrder(TRIP_ID)).thenReturn(List.of());
        when(listBlockRepository.save(any(ListBlock.class))).thenAnswer(call -> {
            ListBlock block = call.getArgument(0); block.setId(BLOCK_ID); return block;
        });
        when(blockItemRepository.findByBlockIdOrderByDisplayOrder(BLOCK_ID)).thenReturn(List.of());
        when(tripRepository.saveAndFlush(trip)).thenReturn(trip);

        plannerService.savePlannerSnapshot(TRIP_ID, USER_ID,
                new PlannerSnapshotRequest(1L, List.of(anchoredBlock(null, null))));

        ArgumentCaptor<ListBlock> block = ArgumentCaptor.forClass(ListBlock.class);
        verify(listBlockRepository).save(block.capture());
        assertNull(block.getValue().getStartAnchorId());
        assertNull(block.getValue().getEndAnchorId());
    }

    @Test
    void clearingAnAnchorWipesEveryColumnRatherThanLeavingAHalfAnchor() {
        Trip trip = ownedTrip(1L);
        ListBlock existing = ListBlock.builder().id(BLOCK_ID).tripId(TRIP_ID).clientId("block-day-1")
                .startAnchorId("old").startAnchorKind("PLACE").startAnchorName("Old start")
                .startAnchorAddress("Somewhere").startAnchorLat(1.0).startAnchorLng(2.0)
                .build();
        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).thenReturn(Optional.of(trip));
        when(listBlockRepository.findByTripIdOrderByDisplayOrder(TRIP_ID)).thenReturn(List.of(existing));
        when(listBlockRepository.save(any(ListBlock.class))).thenAnswer(call -> call.getArgument(0));
        when(blockItemRepository.findByBlockIdOrderByDisplayOrder(BLOCK_ID)).thenReturn(List.of());
        when(tripRepository.saveAndFlush(trip)).thenReturn(trip);

        plannerService.savePlannerSnapshot(TRIP_ID, USER_ID,
                new PlannerSnapshotRequest(1L, List.of(anchoredBlock(null, null))));

        assertNull(existing.getStartAnchorId());
        assertNull(existing.getStartAnchorKind());
        assertNull(existing.getStartAnchorName());
        assertNull(existing.getStartAnchorAddress());
        assertNull(existing.getStartAnchorLat());
        assertNull(existing.getStartAnchorLng());
    }

    @Test
    void rejectsAnAnchorKindThatSharingWouldNotKnowHowToHandle() {
        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).thenReturn(Optional.of(ownedTrip(1L)));
        PlannerAnchorDto unknown = new PlannerAnchorDto("x", "SECRET", "Home", null, 13.75, 100.5);

        assertThrows(PlannerService.PlannerValidationException.class,
                () -> plannerService.savePlannerSnapshot(TRIP_ID, USER_ID,
                        new PlannerSnapshotRequest(1L, List.of(anchoredBlock(unknown, null)))));

        verify(listBlockRepository, never()).save(any());
    }

    @Test
    void rejectsAnAnchorWithCoordinatesOutsideTheWorld() {
        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).thenReturn(Optional.of(ownedTrip(1L)));
        PlannerAnchorDto offWorld = new PlannerAnchorDto("x", "PLACE", "Nowhere", null, 120.0, 100.5);

        assertThrows(PlannerService.PlannerValidationException.class,
                () -> plannerService.savePlannerSnapshot(TRIP_ID, USER_ID,
                        new PlannerSnapshotRequest(1L, List.of(anchoredBlock(null, offWorld)))));

        verify(listBlockRepository, never()).save(any());
    }

    private PlannerBlockDto anchoredBlock(PlannerAnchorDto start, PlannerAnchorDto end) {
        return new PlannerBlockDto("block-day-1", "itinerary", "Day 1",
                LocalDate.of(2026, 9, 1), "amber", List.of(), null, start, end);
    }

    @Test
    void rejectsChargeTargetsOutsideBatteryCapacityBeforeWriting() {
        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).thenReturn(Optional.of(ownedTrip(1L)));
        assertThrows(PlannerService.PlannerValidationException.class, () -> plannerService.savePlannerSnapshot(TRIP_ID, USER_ID, chargerRequest(1L, "Open 24 hours", 101)));
        verify(blockItemRepository, never()).save(any());
    }

    private String savedOpeningHours(String openingHoursSummary) {
        Trip trip = ownedTrip(1L);
        ListBlock savedBlock = ListBlock.builder().id(BLOCK_ID).tripId(TRIP_ID).build();

        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).thenReturn(Optional.of(trip));
        when(listBlockRepository.findByTripIdOrderByDisplayOrder(TRIP_ID)).thenReturn(List.of());
        when(listBlockRepository.save(any(ListBlock.class))).thenReturn(savedBlock);
        when(blockItemRepository.findByBlockIdOrderByDisplayOrder(BLOCK_ID)).thenReturn(List.of());
        when(blockItemRepository.save(any(BlockItem.class))).thenAnswer(invocation -> {
            BlockItem item = invocation.getArgument(0);
            item.setId(ITEM_ID);
            return item;
        });
        when(tripRepository.saveAndFlush(trip)).thenReturn(trip);

        plannerService.savePlannerSnapshot(TRIP_ID, USER_ID, chargerRequest(1L, openingHoursSummary));

        ArgumentCaptor<BlockItem> saved = ArgumentCaptor.forClass(BlockItem.class);
        verify(blockItemRepository).save(saved.capture());
        return saved.getValue().getOpeningHours();
    }

    private PlannerSnapshotRequest chargerRequest(long version, String openingHoursSummary) {
        return chargerRequest(version, openingHoursSummary, null);
    }

    private PlannerSnapshotRequest chargerRequest(long version, String openingHoursSummary, Integer target) {
        PlannerEvChargerDto charger = new PlannerEvChargerDto(
                List.of("CCS2"),
                150.0,
                4,
                2,
                "Price not listed",
                openingHoursSummary,
                25,
                "Operator", "MANUAL", false, target);
        PlannerItemDto place = new PlannerItemDto(
                "charger-1",
                "place",
                "ev-charger:station-1",
                "Charging station",
                null,
                "Address",
                13.0,
                100.0,
                4.5,
                10,
                null,
                null,
                false,
                null,
                null,
                null,
                charger,
                null,
                "Charging station",
                List.of());
        PlannerBlockDto block = new PlannerBlockDto(
                "block-list-1",
                "list",
                "Day 1",
                LocalDate.of(2026, 9, 1),
                "amber",
                List.of(place));
        return new PlannerSnapshotRequest(version, List.of(block));
    }

    private PlannerSnapshotRequest checklistRequest(long version) {
        PlannerChecklistSubItemDto subItem = new PlannerChecklistSubItemDto(
                "checklist-row-1",
                "Passport and visa",
                true);
        PlannerItemDto checklist = new PlannerItemDto(
                "checklist-1",
                "checklist",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Documents",
                List.of(subItem));
        PlannerBlockDto block = new PlannerBlockDto(
                "block-list-1",
                "list",
                "Packing",
                LocalDate.of(2026, 9, 1),
                "amber",
                List.of(checklist));
        return new PlannerSnapshotRequest(version, List.of(block));
    }
}
