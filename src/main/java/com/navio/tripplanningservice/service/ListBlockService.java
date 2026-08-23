package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.dto.CreateListBlockRequest;
import com.navio.tripplanningservice.dto.ListBlockResponse;
import com.navio.tripplanningservice.model.ListBlock;
import com.navio.tripplanningservice.repository.ListBlockRepository;
import com.navio.tripplanningservice.repository.BlockItemRepository;
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
public class ListBlockService {

    private final ListBlockRepository listBlockRepository;
    private final TripRepository tripRepository;
    private final BlockItemService blockItemService;
    private final BlockItemRepository blockItemRepository;

    @Transactional
    public ListBlockResponse createListBlock(UUID tripId, UUID userId, CreateListBlockRequest request) {
        // Verify trip exists and user owns it
        com.navio.tripplanningservice.model.Trip trip = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripService.TripNotFoundException(tripId));

        ListBlock block = ListBlock.builder()
                .tripId(tripId)
                .clientId(UUID.randomUUID().toString())
                .name(request.getName())
                .type(ListBlock.ListBlockType.valueOf(request.getType()))
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .blockColor(request.getBlockColor())
                .blockDate(trip.getStartDate())
                .build();

        ListBlock savedBlock = listBlockRepository.save(block);
        return mapToResponse(savedBlock);
    }

    public ListBlockResponse getListBlock(UUID blockId, UUID tripId, UUID userId) {
        // Verify trip exists and user owns it
        tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripService.TripNotFoundException(tripId));

        ListBlock block = listBlockRepository.findByIdAndTripId(blockId, tripId)
                .orElseThrow(() -> new ListBlockNotFoundException(blockId));
        return mapToResponse(block);
    }

    public List<ListBlockResponse> getTripListBlocks(UUID tripId, UUID userId) {
        // Verify trip exists and user owns it
        tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripService.TripNotFoundException(tripId));

        return listBlockRepository.findByTripIdOrderByDisplayOrder(tripId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ListBlockResponse updateListBlock(UUID blockId, UUID tripId, UUID userId, CreateListBlockRequest request) {
        // Verify trip exists and user owns it
        tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripService.TripNotFoundException(tripId));

        ListBlock block = listBlockRepository.findByIdAndTripId(blockId, tripId)
                .orElseThrow(() -> new ListBlockNotFoundException(blockId));

        block.setName(request.getName());
        block.setType(ListBlock.ListBlockType.valueOf(request.getType()));
        if (request.getDisplayOrder() != null) {
            block.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getBlockColor() != null) {
            block.setBlockColor(request.getBlockColor());
        }

        ListBlock updatedBlock = listBlockRepository.save(block);
        return mapToResponse(updatedBlock);
    }

    @Transactional
    public void deleteListBlock(UUID blockId, UUID tripId, UUID userId) {
        // Verify trip exists and user owns it
        tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripService.TripNotFoundException(tripId));

        ListBlock block = listBlockRepository.findByIdAndTripId(blockId, tripId)
                .orElseThrow(() -> new ListBlockNotFoundException(blockId));

        listBlockRepository.delete(block);
    }

    private ListBlockResponse mapToResponse(ListBlock block) {
        List<com.navio.tripplanningservice.dto.BlockItemResponse> items =
            blockItemRepository.findByBlockIdOrderByDisplayOrder(block.getId()).stream()
                .map(blockItemService::mapToResponse)
                .collect(Collectors.toList());

        return ListBlockResponse.builder()
                .id(block.getId())
                .name(block.getName())
                .type(block.getType().toString())
                .displayOrder(block.getDisplayOrder())
                .blockColor(block.getBlockColor())
                .items(items)
                .createdAt(block.getCreatedAt())
                .updatedAt(block.getUpdatedAt())
                .build();
    }

    public static class ListBlockNotFoundException extends RuntimeException {
        public ListBlockNotFoundException(UUID blockId) {
            super("List block not found: " + blockId);
        }
    }
}
