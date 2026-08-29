package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.PlannerSnapshotResponse;
import com.navio.tripplanningservice.dto.TripEvOptimizationPreviewResponse;
import com.navio.tripplanningservice.dto.TripEvOptimizationRequest;
import com.navio.tripplanningservice.service.TripEvOptimizationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/trips/{tripId}/ev-optimization")
public class TripEvOptimizationController {

    private final TripEvOptimizationService optimizationService;

    public TripEvOptimizationController(TripEvOptimizationService optimizationService) {
        this.optimizationService = optimizationService;
    }

    @PostMapping("/preview")
    public ResponseEntity<TripEvOptimizationPreviewResponse> preview(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID tripId,
            @Valid @RequestBody TripEvOptimizationRequest request
    ) {
        return ResponseEntity.ok(optimizationService.preview(tripId, userId, request));
    }

    @PostMapping("/apply")
    public ResponseEntity<PlannerSnapshotResponse> apply(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID tripId,
            @Valid @RequestBody TripEvOptimizationRequest request
    ) {
        return ResponseEntity.ok(optimizationService.apply(tripId, userId, request));
    }
}
