package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.CreateListBlockRequest;
import com.navio.tripplanningservice.dto.ListBlockResponse;
import com.navio.tripplanningservice.service.ListBlockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/trips/{tripId}/blocks")
@RequiredArgsConstructor
public class ListBlockController {

    private final ListBlockService listBlockService;

    @PostMapping
    public ResponseEntity<ListBlockResponse> createListBlock(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateListBlockRequest request) {
        ListBlockResponse response = listBlockService.createListBlock(tripId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{blockId}")
    public ResponseEntity<ListBlockResponse> getListBlock(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId,
            @PathVariable UUID blockId) {
        ListBlockResponse response = listBlockService.getListBlock(blockId, tripId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ListBlockResponse>> getTripListBlocks(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId) {
        List<ListBlockResponse> response = listBlockService.getTripListBlocks(tripId, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{blockId}")
    public ResponseEntity<ListBlockResponse> updateListBlock(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId,
            @PathVariable UUID blockId,
            @Valid @RequestBody CreateListBlockRequest request) {
        ListBlockResponse response = listBlockService.updateListBlock(blockId, tripId, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{blockId}")
    public ResponseEntity<Void> deleteListBlock(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId,
            @PathVariable UUID blockId) {
        listBlockService.deleteListBlock(blockId, tripId, userId);
        return ResponseEntity.noContent().build();
    }
}
