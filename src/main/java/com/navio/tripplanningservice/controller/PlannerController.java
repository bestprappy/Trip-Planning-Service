package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.PlannerSnapshotRequest;
import com.navio.tripplanningservice.dto.PlannerSnapshotResponse;
import com.navio.tripplanningservice.dto.PlannerSaveResponse;
import com.navio.tripplanningservice.service.PlannerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/trips/{tripId}/planner")
@RequiredArgsConstructor
public class PlannerController {

    private final PlannerService plannerService;

    @GetMapping
    public ResponseEntity<PlannerSnapshotResponse> getPlanner(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId) {
        return ResponseEntity.ok(plannerService.getPlannerSnapshot(tripId, userId));
    }

    @PutMapping
    public ResponseEntity<PlannerSaveResponse> savePlanner(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId,
            @Valid @RequestBody PlannerSnapshotRequest request) {
        return ResponseEntity.ok(plannerService.savePlannerSnapshot(tripId, userId, request));
    }
}
