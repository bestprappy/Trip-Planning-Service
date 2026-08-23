package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.dto.PlannerBlockDto;
import com.navio.tripplanningservice.dto.PlannerChecklistSubItemDto;
import com.navio.tripplanningservice.dto.PlannerEvChargerDto;
import com.navio.tripplanningservice.dto.PlannerItemDto;
import com.navio.tripplanningservice.dto.PlannerSaveResponse;
import com.navio.tripplanningservice.dto.PlannerSnapshotRequest;
import com.navio.tripplanningservice.dto.PlannerSnapshotResponse;
import com.navio.tripplanningservice.model.BlockItem;
import com.navio.tripplanningservice.model.ChecklistSubItem;
import com.navio.tripplanningservice.model.ListBlock;
import com.navio.tripplanningservice.model.Trip;
import com.navio.tripplanningservice.repository.BlockItemRepository;
import com.navio.tripplanningservice.repository.ChecklistSubItemRepository;
import com.navio.tripplanningservice.repository.ListBlockRepository;
import com.navio.tripplanningservice.repository.TripRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlannerService {

    private final TripRepository tripRepository;
    private final ListBlockRepository listBlockRepository;
    private final BlockItemRepository blockItemRepository;
    private final ChecklistSubItemRepository checklistSubItemRepository;
    private final MeterRegistry meterRegistry;

    public PlannerSnapshotResponse getPlannerSnapshot(UUID tripId, UUID userId) {
        Trip trip = requireOwnedTrip(tripId, userId);
        return mapSnapshot(tripId, trip.getVersion(), trip.getUpdatedAt());
    }

    @Transactional
    public PlannerSaveResponse savePlannerSnapshot(
            UUID tripId,
            UUID userId,
            PlannerSnapshotRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            Trip trip = requireOwnedTrip(tripId, userId);
            validateSnapshot(request);
            if (!Objects.equals(request.version(), trip.getVersion())) {
                throw new ObjectOptimisticLockingFailureException(Trip.class, tripId);
            }

            Map<String, ListBlock> existingBlocks = listBlockRepository
                    .findByTripIdOrderByDisplayOrder(tripId)
                    .stream()
                    .collect(Collectors.toMap(
                            ListBlock::getClientId,
                            block -> block,
                            (first, ignored) -> first,
                            LinkedHashMap::new));

            List<PlannerBlockDto> requestedBlocks = nullSafe(request.blocks());
            for (int blockIndex = 0; blockIndex < requestedBlocks.size(); blockIndex++) {
                PlannerBlockDto blockDto = requestedBlocks.get(blockIndex);
                ListBlock block = existingBlocks.remove(blockDto.id());
                if (block == null) {
                    block = ListBlock.builder()
                            .tripId(tripId)
                            .clientId(blockDto.id())
                            .build();
                }

                applyBlock(block, blockDto, blockIndex);
                ListBlock savedBlock = listBlockRepository.save(block);
                syncItems(savedBlock.getId(), blockDto.items());
            }

            existingBlocks.values().forEach(this::deleteBlockWithChildren);
            trip.setUpdatedAt(Instant.now());
            Trip savedTrip = tripRepository.saveAndFlush(trip);
            return new PlannerSaveResponse(savedTrip.getVersion(), savedTrip.getUpdatedAt());
        } catch (RuntimeException exception) {
            outcome = "failure";
            throw exception;
        } finally {
            Timer autosaveTimer = Timer.builder("navio.planner.autosave")
                    .description("Trip planner autosave request latency")
                    .tag("outcome", outcome)
                    .publishPercentileHistogram()
                    .register(meterRegistry);
            sample.stop(autosaveTimer);
        }
    }

    private Trip requireOwnedTrip(UUID tripId, UUID userId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripService.TripNotFoundException(tripId));
    }

    private void applyBlock(ListBlock block, PlannerBlockDto dto, int displayOrder) {
        block.setClientId(dto.id());
        block.setName(dto.title());
        block.setType(parseBlockKind(dto.kind()));
        block.setDisplayOrder(displayOrder);
        block.setBlockColor(dto.colorId());
        block.setBlockDate(dto.date());
    }

    private void syncItems(UUID blockId, List<PlannerItemDto> requestedItemList) {
        Map<String, BlockItem> existingItems = blockItemRepository
                .findByBlockIdOrderByDisplayOrder(blockId)
                .stream()
                .collect(Collectors.toMap(
                        BlockItem::getClientId,
                        item -> item,
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        List<PlannerItemDto> requestedItems = nullSafe(requestedItemList);
        for (int itemIndex = 0; itemIndex < requestedItems.size(); itemIndex++) {
            PlannerItemDto itemDto = requestedItems.get(itemIndex);
            BlockItem item = existingItems.remove(itemDto.id());
            if (item == null) {
                item = BlockItem.builder()
                        .blockId(blockId)
                        .clientId(itemDto.id())
                        .build();
            }

            applyItem(item, itemDto, itemIndex);
            BlockItem savedItem = blockItemRepository.save(item);

            if (savedItem.getType() == BlockItem.BlockItemType.CHECKLIST) {
                syncChecklistItems(savedItem.getId(), itemDto.items());
            } else {
                checklistSubItemRepository.deleteByItemId(savedItem.getId());
            }
        }

        existingItems.values().forEach(this::deleteItemWithChecklist);
    }

    private void applyItem(BlockItem item, PlannerItemDto dto, int displayOrder) {
        BlockItem.BlockItemType itemType = parseItemType(dto.type());
        item.setClientId(dto.id());
        item.setType(itemType);
        item.setDisplayOrder(displayOrder);
        item.setTitle(resolveTitle(dto, itemType));
        item.setNotes(itemType == BlockItem.BlockItemType.NOTE ? dto.content() : dto.notes());

        item.setPlaceId(dto.placeId());
        item.setPlaceName(dto.name());
        item.setPlaceDescription(dto.description());
        item.setPlaceAddress(dto.address());
        item.setPlaceLat(dto.lat());
        item.setPlaceLng(dto.lng());
        item.setPlaceRating(dto.rating());
        item.setPlaceReviewCount(dto.reviewCount());
        item.setPlaceImageUrl(dto.imageUrl());
        item.setVisited(Boolean.TRUE.equals(dto.isVisited()));
        item.setStartTime(parseTime(dto.time(), "time"));
        item.setEndTime(parseTime(dto.timeEnd(), "timeEnd"));
        item.setEstimatedCost(toMoney(dto.cost()));

        PlannerEvChargerDto evCharger = dto.evCharger();
        if (evCharger == null) {
            clearEvSnapshot(item);
        } else {
            item.setEvConnectorTypes(String.join(",", nullSafe(evCharger.connectorTypes())));
            item.setPowerKw(evCharger.maxKw());
            item.setEvTotalConnectors(evCharger.totalConnectors());
            item.setEvAvailableConnectors(evCharger.availableConnectors());
            item.setAvailablePlugs(evCharger.availableConnectors());
            item.setPrice(evCharger.priceText());
            item.setOpeningHours(evCharger.openingHoursSummary());
            item.setEstimatedChargeMinutes(evCharger.estimatedChargeMinutes());
            item.setEvOperatorName(evCharger.operatorName());
            item.setIsCharged(Boolean.TRUE.equals(dto.isVisited()));
        }
    }

    private void syncChecklistItems(
            UUID itemId,
            List<PlannerChecklistSubItemDto> requestedSubItemList) {
        Map<String, ChecklistSubItem> existingSubItems = checklistSubItemRepository
                .findByItemIdOrderByDisplayOrder(itemId)
                .stream()
                .collect(Collectors.toMap(
                        ChecklistSubItem::getClientId,
                        subItem -> subItem,
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        List<PlannerChecklistSubItemDto> requestedSubItems = nullSafe(requestedSubItemList);
        for (int index = 0; index < requestedSubItems.size(); index++) {
            PlannerChecklistSubItemDto dto = requestedSubItems.get(index);
            ChecklistSubItem subItem = existingSubItems.remove(dto.id());
            if (subItem == null) {
                subItem = ChecklistSubItem.builder()
                        .itemId(itemId)
                        .clientId(dto.id())
                        .build();
            }
            subItem.setLabel(dto.label());
            subItem.setChecked(dto.checked());
            subItem.setDisplayOrder(index);
            checklistSubItemRepository.save(subItem);
        }

        checklistSubItemRepository.deleteAll(existingSubItems.values());
    }

    private void clearEvSnapshot(BlockItem item) {
        item.setEvConnectorTypes(null);
        item.setPowerKw(null);
        item.setEvTotalConnectors(null);
        item.setEvAvailableConnectors(null);
        item.setAvailablePlugs(null);
        item.setPrice(null);
        item.setOpeningHours(null);
        item.setEstimatedChargeMinutes(null);
        item.setEvOperatorName(null);
        item.setIsCharged(false);
    }

    private void deleteBlockWithChildren(ListBlock block) {
        blockItemRepository.findByBlockIdOrderByDisplayOrder(block.getId())
                .forEach(this::deleteItemWithChecklist);
        listBlockRepository.delete(block);
    }

    private void deleteItemWithChecklist(BlockItem item) {
        checklistSubItemRepository.deleteByItemId(item.getId());
        blockItemRepository.delete(item);
    }

    private PlannerSnapshotResponse mapSnapshot(UUID tripId, long version, Instant savedAt) {
        List<PlannerBlockDto> blocks = listBlockRepository
                .findByTripIdOrderByDisplayOrder(tripId)
                .stream()
                .map(this::mapBlock)
                .toList();
        return new PlannerSnapshotResponse(blocks, version, savedAt);
    }

    private PlannerBlockDto mapBlock(ListBlock block) {
        List<PlannerItemDto> items = blockItemRepository
                .findByBlockIdOrderByDisplayOrder(block.getId())
                .stream()
                .map(this::mapItem)
                .toList();
        String kind = block.getType() == ListBlock.ListBlockType.ITINERARY
                ? "itinerary"
                : "list";
        return new PlannerBlockDto(
                block.getClientId(),
                kind,
                block.getName(),
                block.getBlockDate(),
                block.getBlockColor(),
                items);
    }

    private PlannerItemDto mapItem(BlockItem item) {
        String itemType = switch (item.getType()) {
            case NOTE -> "note";
            case CHECKLIST, CHECKLIST_ITEM -> "checklist";
            default -> "place";
        };

        List<PlannerChecklistSubItemDto> checklistItems = "checklist".equals(itemType)
                ? checklistSubItemRepository.findByItemIdOrderByDisplayOrder(item.getId())
                        .stream()
                        .map(subItem -> new PlannerChecklistSubItemDto(
                                subItem.getClientId(),
                                subItem.getLabel(),
                                subItem.getChecked()))
                        .toList()
                : null;

        PlannerEvChargerDto evCharger = hasEvSnapshot(item)
                ? new PlannerEvChargerDto(
                        splitConnectorTypes(item.getEvConnectorTypes()),
                        item.getPowerKw(),
                        item.getEvTotalConnectors(),
                        item.getEvAvailableConnectors(),
                        item.getPrice(),
                        item.getOpeningHours(),
                        item.getEstimatedChargeMinutes(),
                        item.getEvOperatorName())
                : null;

        return new PlannerItemDto(
                item.getClientId(),
                itemType,
                item.getPlaceId(),
                item.getPlaceName(),
                item.getPlaceDescription(),
                item.getPlaceAddress(),
                item.getPlaceLat(),
                item.getPlaceLng(),
                item.getPlaceRating(),
                item.getPlaceReviewCount(),
                item.getPlaceImageUrl(),
                "place".equals(itemType) ? item.getNotes() : null,
                "place".equals(itemType) ? item.getVisited() : null,
                formatTime(item.getStartTime()),
                formatTime(item.getEndTime()),
                item.getEstimatedCost() == null ? null : item.getEstimatedCost().doubleValue(),
                evCharger,
                "note".equals(itemType) ? Objects.requireNonNullElse(item.getNotes(), "") : null,
                "checklist".equals(itemType) ? item.getTitle() : null,
                checklistItems);
    }

    private void validateSnapshot(PlannerSnapshotRequest request) {
        Set<String> blockIds = new java.util.HashSet<>();
        for (PlannerBlockDto block : nullSafe(request.blocks())) {
            if (!blockIds.add(block.id())) {
                throw new PlannerValidationException("Duplicate block id: " + block.id());
            }
            parseBlockKind(block.kind());

            Set<String> itemIds = new java.util.HashSet<>();
            for (PlannerItemDto item : nullSafe(block.items())) {
                if (!itemIds.add(item.id())) {
                    throw new PlannerValidationException("Duplicate item id in block " + block.id());
                }
                BlockItem.BlockItemType itemType = parseItemType(item.type());
                if (itemType == BlockItem.BlockItemType.PLACE) {
                    validatePlace(item);
                } else if (itemType == BlockItem.BlockItemType.NOTE && item.content() == null) {
                    throw new PlannerValidationException("Note items require content");
                } else if (itemType == BlockItem.BlockItemType.CHECKLIST && item.title() == null) {
                    throw new PlannerValidationException("Checklist items require a title");
                }

                Set<String> subItemIds = new java.util.HashSet<>();
                for (PlannerChecklistSubItemDto subItem : nullSafe(item.items())) {
                    if (!subItemIds.add(subItem.id())) {
                        throw new PlannerValidationException("Duplicate checklist item id in " + item.id());
                    }
                }
            }
        }
    }

    private void validatePlace(PlannerItemDto item) {
        if (isBlank(item.placeId()) || isBlank(item.name())) {
            throw new PlannerValidationException("Place items require a place id and name");
        }
        if (item.address() == null || item.lat() == null || item.lng() == null) {
            throw new PlannerValidationException("Place items require an address and coordinates");
        }
        if (!Double.isFinite(item.lat()) || item.lat() < -90 || item.lat() > 90
                || !Double.isFinite(item.lng()) || item.lng() < -180 || item.lng() > 180) {
            throw new PlannerValidationException("Place coordinates are invalid");
        }
        if (item.reviewCount() != null && item.reviewCount() < 0) {
            throw new PlannerValidationException("Place review count cannot be negative");
        }
        PlannerEvChargerDto evCharger = item.evCharger();
        if (evCharger != null
                && evCharger.availableConnectors() != null
                && evCharger.totalConnectors() != null
                && evCharger.availableConnectors() > evCharger.totalConnectors()) {
            throw new PlannerValidationException(
                    "Available EV connectors cannot exceed total connectors");
        }
    }

    private ListBlock.ListBlockType parseBlockKind(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "itinerary" -> ListBlock.ListBlockType.ITINERARY;
            case "list" -> ListBlock.ListBlockType.LIST;
            default -> throw new PlannerValidationException("Unsupported block kind: " + value);
        };
    }

    private BlockItem.BlockItemType parseItemType(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "place" -> BlockItem.BlockItemType.PLACE;
            case "note" -> BlockItem.BlockItemType.NOTE;
            case "checklist" -> BlockItem.BlockItemType.CHECKLIST;
            default -> throw new PlannerValidationException("Unsupported planner item type: " + value);
        };
    }

    private String resolveTitle(PlannerItemDto dto, BlockItem.BlockItemType type) {
        return switch (type) {
            case PLACE, EV_STATION -> Objects.requireNonNullElse(dto.name(), "Place");
            case CHECKLIST, CHECKLIST_ITEM -> Objects.requireNonNullElse(dto.title(), "");
            case NOTE -> "Note";
            default -> Objects.requireNonNullElse(dto.title(), type.name());
        };
    }

    private LocalTime parseTime(String value, String fieldName) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new PlannerValidationException(fieldName + " must use HH:mm format");
        }
    }

    private BigDecimal toMoney(Double value) {
        if (value == null) {
            return null;
        }
        if (!Double.isFinite(value) || value < 0) {
            throw new PlannerValidationException("cost must be a finite non-negative number");
        }
        return BigDecimal.valueOf(value);
    }

    private boolean hasEvSnapshot(BlockItem item) {
        return item.getEvConnectorTypes() != null
                || item.getPowerKw() != null
                || item.getEvTotalConnectors() != null;
    }

    private List<String> splitConnectorTypes(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String connector : value.split(",")) {
            if (!connector.isBlank()) {
                values.add(connector.trim());
            }
        }
        return values;
    }

    private String formatTime(LocalTime value) {
        if (value == null) {
            return null;
        }
        return value.withSecond(0).withNano(0).toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public static class PlannerValidationException extends RuntimeException {
        public PlannerValidationException(String message) {
            super(message);
        }
    }
}
