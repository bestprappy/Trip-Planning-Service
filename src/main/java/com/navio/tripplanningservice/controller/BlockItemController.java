package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.BlockItemResponse;
import com.navio.tripplanningservice.dto.CreateBlockItemRequest;
import com.navio.tripplanningservice.service.BlockItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/trips/{tripId}/blocks/{blockId}/items")
@RequiredArgsConstructor
public class BlockItemController {

    private final BlockItemService blockItemService;

    @PostMapping
    public ResponseEntity<BlockItemResponse> createBlockItem(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId,
            @PathVariable UUID blockId,
            @Valid @RequestBody CreateBlockItemRequest request) {
        BlockItemResponse response = blockItemService.createBlockItem(blockId, tripId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<BlockItemResponse> getBlockItem(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId,
            @PathVariable UUID blockId,
            @PathVariable UUID itemId) {
        BlockItemResponse response = blockItemService.getBlockItem(itemId, blockId, tripId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<BlockItemResponse>> getBlockItems(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId,
            @PathVariable UUID blockId) {
        List<BlockItemResponse> response = blockItemService.getBlockItems(blockId, tripId, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<BlockItemResponse> updateBlockItem(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId,
            @PathVariable UUID blockId,
            @PathVariable UUID itemId,
            @Valid @RequestBody CreateBlockItemRequest request) {
        BlockItemResponse response = blockItemService.updateBlockItem(itemId, blockId, tripId, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> deleteBlockItem(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId,
            @PathVariable UUID blockId,
            @PathVariable UUID itemId) {
        blockItemService.deleteBlockItem(itemId, blockId, tripId, userId);
        return ResponseEntity.noContent().build();
    }
}
