package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.dto.PlannerBlockDto;
import com.navio.tripplanningservice.dto.PlannerChecklistSubItemDto;
import com.navio.tripplanningservice.dto.PlannerItemDto;
import com.navio.tripplanningservice.dto.PlannerSaveResponse;
import com.navio.tripplanningservice.dto.PlannerSnapshotRequest;
import com.navio.tripplanningservice.model.BlockItem;
import com.navio.tripplanningservice.model.ChecklistSubItem;
import com.navio.tripplanningservice.model.ListBlock;
import com.navio.tripplanningservice.model.Trip;
import com.navio.tripplanningservice.repository.BlockItemRepository;
import com.navio.tripplanningservice.repository.ChecklistSubItemRepository;
import com.navio.tripplanningservice.repository.ListBlockRepository;
import com.navio.tripplanningservice.repository.TripRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private Trip ownedTrip(long version) {
        return Trip.builder()
                .id(TRIP_ID)
                .userId(USER_ID)
                .version(version)
                .build();
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
