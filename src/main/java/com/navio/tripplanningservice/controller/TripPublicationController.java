package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.publication.PreviewPlanRequest;
import com.navio.tripplanningservice.dto.publication.PublicPlanSnapshot;
import com.navio.tripplanningservice.dto.publication.PublicationResponse;
import com.navio.tripplanningservice.dto.publication.PublishPlanRequest;
import com.navio.tripplanningservice.dto.publication.UpdateExploreListingRequest;
import com.navio.tripplanningservice.service.publication.TripPublicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Owner-only management of a trip's published link.
 *
 * <p>Every method passes {@code userId} into the service rather than checking
 * anything here; the service resolves the trip by id <em>and</em> owner, so a
 * stranger's request cannot get further than a 404. A method on this controller
 * that did not take {@code X-User-Id} would be the bug to look for.
 */
@RestController
@RequestMapping("/v1/trips")
@RequiredArgsConstructor
public class TripPublicationController {

    private final TripPublicationService publicationService;

    @GetMapping("/{tripId}/publication")
    public ResponseEntity<PublicationResponse> getPublication(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId) {
        return ResponseEntity.ok(publicationService.getPublication(tripId, userId));
    }

    /**
     * POST rather than GET: the options are a body, and a preview URL containing
     * the owner's inclusion choices would end up in browser history and proxy
     * logs. Nothing is persisted.
     */
    @PostMapping("/{tripId}/publication/preview")
    public ResponseEntity<PublicPlanSnapshot> previewPublication(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId,
            @Valid @RequestBody PreviewPlanRequest request) {
        return ResponseEntity.ok(publicationService.preview(tripId, userId, request.safeOptions()));
    }

    /** Create or update; same URL either way, because a trip has one link. */
    @PutMapping("/{tripId}/publication")
    public ResponseEntity<PublicationResponse> publish(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId,
            @Valid @RequestBody PublishPlanRequest request) {
        return ResponseEntity.ok(publicationService.publish(tripId, userId, request));
    }

    /** List on or remove from Explore; immediate, content unchanged. */
    @PatchMapping("/{tripId}/publication")
    public ResponseEntity<PublicationResponse> updateExploreListing(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId,
            @Valid @RequestBody UpdateExploreListingRequest request) {
        return ResponseEntity.ok(publicationService.updateExploreListing(
                tripId, userId, request.listedInExplore(), request.authorDisplayName()));
    }

    @DeleteMapping("/{tripId}/publication")
    public ResponseEntity<Void> stopSharing(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId) {
        publicationService.revoke(tripId, userId);
        return ResponseEntity.noContent().build();
    }
}
