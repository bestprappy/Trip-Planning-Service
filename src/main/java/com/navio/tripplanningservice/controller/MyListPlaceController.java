package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.AppendMyListPlaceRequest;
import com.navio.tripplanningservice.dto.AppendMyListPlaceResponse;
import com.navio.tripplanningservice.service.MyListPlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/v1/trips/{tripId}/planner/my-list/places")
@RequiredArgsConstructor
public class MyListPlaceController {
    private final MyListPlaceService service;

    @PostMapping
    public AppendMyListPlaceResponse append(@RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID tripId, @Valid @RequestBody AppendMyListPlaceRequest request) {
        return service.append(tripId, userId, request);
    }
}
