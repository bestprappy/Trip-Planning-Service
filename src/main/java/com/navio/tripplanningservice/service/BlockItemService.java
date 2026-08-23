package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.dto.BlockItemResponse;
import com.navio.tripplanningservice.dto.CreateBlockItemRequest;
import com.navio.tripplanningservice.model.BlockItem;
import com.navio.tripplanningservice.repository.BlockItemRepository;
import com.navio.tripplanningservice.repository.ListBlockRepository;
import com.navio.tripplanningservice.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockItemService {

    private final BlockItemRepository blockItemRepository;
    private final ListBlockRepository listBlockRepository;
    private final TripRepository tripRepository;

    @Transactional
    public BlockItemResponse createBlockItem(UUID blockId, UUID tripId, UUID userId, CreateBlockItemRequest request) {
        // Verify block exists and belongs to the user's trip
        verifyBlockBelongsToUserTrip(blockId, tripId, userId);

        BlockItem item = BlockItem.builder()
                .blockId(blockId)
                .clientId(UUID.randomUUID().toString())
                .type(BlockItem.BlockItemType.valueOf(request.getType()))
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .title(request.getTitle())
                .notes(request.getNotes())
                // Place fields
                .placeId(request.getPlaceId())
                .placeName(request.getPlaceName())
                .placeLat(request.getPlaceLat())
                .placeLng(request.getPlaceLng())
                .placeType(request.getPlaceType())
                .placeAddress(request.getPlaceAddress())
                .placePhone(request.getPlacePhone())
                .placeWebsite(request.getPlaceWebsite())
                .placeRating(request.getPlaceRating())
                .estimatedTime(request.getEstimatedTime())
                // EV Station fields
                .stationId(request.getStationId())
                .stationName(request.getStationName())
                .stationAddress(request.getStationAddress())
                .stationLat(request.getStationLat())
                .stationLng(request.getStationLng())
                .chargerType(request.getChargerType())
                .connectorType(request.getConnectorType())
                .availablePlugs(request.getAvailablePlugs())
                .powerKw(request.getPowerKw())
                .estimatedChargeMinutes(request.getEstimatedChargeMinutes())
                .batteryPercentage(request.getBatteryPercentage())
                .price(request.getPrice())
                .openingHours(request.getOpeningHours())
                .isCharged(request.getIsCharged())
                // Checklist fields
                .completed(request.getCompleted() != null ? request.getCompleted() : false)
                // Reservation fields
                .reservationId(request.getReservationId())
                .reservationType(request.getReservationType())
                .reservationDetails(request.getReservationDetails())
                .build();

        BlockItem savedItem = blockItemRepository.save(item);
        return mapToResponse(savedItem);
    }

    public BlockItemResponse getBlockItem(UUID itemId, UUID blockId, UUID tripId, UUID userId) {
        verifyBlockBelongsToUserTrip(blockId, tripId, userId);

        BlockItem item = blockItemRepository.findByIdAndBlockId(itemId, blockId)
                .orElseThrow(() -> new BlockItemNotFoundException(itemId));
        return mapToResponse(item);
    }

    public List<BlockItemResponse> getBlockItems(UUID blockId, UUID tripId, UUID userId) {
        verifyBlockBelongsToUserTrip(blockId, tripId, userId);

        return blockItemRepository.findByBlockIdOrderByDisplayOrder(blockId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public BlockItemResponse updateBlockItem(UUID itemId, UUID blockId, UUID tripId, UUID userId, CreateBlockItemRequest request) {
        verifyBlockBelongsToUserTrip(blockId, tripId, userId);

        BlockItem item = blockItemRepository.findByIdAndBlockId(itemId, blockId)
                .orElseThrow(() -> new BlockItemNotFoundException(itemId));

        item.setType(BlockItem.BlockItemType.valueOf(request.getType()));
        item.setTitle(request.getTitle());
        item.setNotes(request.getNotes());
        if (request.getDisplayOrder() != null) {
            item.setDisplayOrder(request.getDisplayOrder());
        }

        // Update place fields
        item.setPlaceId(request.getPlaceId());
        item.setPlaceName(request.getPlaceName());
        item.setPlaceLat(request.getPlaceLat());
        item.setPlaceLng(request.getPlaceLng());
        item.setPlaceType(request.getPlaceType());
        item.setPlaceAddress(request.getPlaceAddress());
        item.setPlacePhone(request.getPlacePhone());
        item.setPlaceWebsite(request.getPlaceWebsite());
        item.setPlaceRating(request.getPlaceRating());
        item.setEstimatedTime(request.getEstimatedTime());

        // Update EV station fields
        item.setStationId(request.getStationId());
        item.setStationName(request.getStationName());
        item.setStationAddress(request.getStationAddress());
        item.setStationLat(request.getStationLat());
        item.setStationLng(request.getStationLng());
        item.setChargerType(request.getChargerType());
        item.setConnectorType(request.getConnectorType());
        item.setAvailablePlugs(request.getAvailablePlugs());
        item.setPowerKw(request.getPowerKw());
        item.setEstimatedChargeMinutes(request.getEstimatedChargeMinutes());
        item.setBatteryPercentage(request.getBatteryPercentage());
        item.setPrice(request.getPrice());
        item.setOpeningHours(request.getOpeningHours());
        item.setIsCharged(request.getIsCharged());

        // Update checklist fields
        if (request.getCompleted() != null) {
            item.setCompleted(request.getCompleted());
        }

        // Update reservation fields
        item.setReservationId(request.getReservationId());
        item.setReservationType(request.getReservationType());
        item.setReservationDetails(request.getReservationDetails());

        BlockItem updatedItem = blockItemRepository.save(item);
        return mapToResponse(updatedItem);
    }

    @Transactional
    public void deleteBlockItem(UUID itemId, UUID blockId, UUID tripId, UUID userId) {
        verifyBlockBelongsToUserTrip(blockId, tripId, userId);

        BlockItem item = blockItemRepository.findByIdAndBlockId(itemId, blockId)
                .orElseThrow(() -> new BlockItemNotFoundException(itemId));

        blockItemRepository.delete(item);
    }

    public BlockItemResponse mapToResponse(BlockItem item) {
        return BlockItemResponse.builder()
                .id(item.getId())
                .type(item.getType().toString())
                .displayOrder(item.getDisplayOrder())
                .title(item.getTitle())
                .notes(item.getNotes())
                .placeId(item.getPlaceId())
                .placeName(item.getPlaceName())
                .placeLat(item.getPlaceLat())
                .placeLng(item.getPlaceLng())
                .placeType(item.getPlaceType())
                .placeAddress(item.getPlaceAddress())
                .placePhone(item.getPlacePhone())
                .placeWebsite(item.getPlaceWebsite())
                .placeRating(item.getPlaceRating())
                .estimatedTime(item.getEstimatedTime())
                .stationId(item.getStationId())
                .stationName(item.getStationName())
                .stationAddress(item.getStationAddress())
                .stationLat(item.getStationLat())
                .stationLng(item.getStationLng())
                .chargerType(item.getChargerType())
                .connectorType(item.getConnectorType())
                .availablePlugs(item.getAvailablePlugs())
                .powerKw(item.getPowerKw())
                .estimatedChargeMinutes(item.getEstimatedChargeMinutes())
                .batteryPercentage(item.getBatteryPercentage())
                .price(item.getPrice())
                .openingHours(item.getOpeningHours())
                .isCharged(item.getIsCharged())
                .completed(item.getCompleted())
                .reservationId(item.getReservationId())
                .reservationType(item.getReservationType())
                .reservationDetails(item.getReservationDetails())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private void verifyBlockBelongsToUserTrip(UUID blockId, UUID tripId, UUID userId) {
        tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripService.TripNotFoundException(tripId));
        listBlockRepository.findByIdAndTripId(blockId, tripId)
                .orElseThrow(() -> new ListBlockService.ListBlockNotFoundException(blockId));
    }

    public static class BlockItemNotFoundException extends RuntimeException {
        public BlockItemNotFoundException(UUID itemId) {
            super("Block item not found: " + itemId);
        }
    }
}
