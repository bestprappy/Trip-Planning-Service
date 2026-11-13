package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.TripCoverResponse;
import com.navio.tripplanningservice.service.TripCoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Upload, read and remove the owner's banner photo for a trip. */
@RestController
@RequestMapping("/v1/trips")
@RequiredArgsConstructor
public class TripCoverController {

    private final TripCoverService covers;

    @PostMapping(value = "/{tripId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TripCoverResponse> upload(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(covers.upload(tripId, userId, file));
    }

    @GetMapping("/{tripId}/cover")
    public ResponseEntity<byte[]> read(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId) {
        var picture = covers.read(tripId, userId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(picture.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(picture.bytes().length)
                .body(picture.bytes());
    }

    @DeleteMapping("/{tripId}/cover")
    public ResponseEntity<Void> remove(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable UUID tripId) {
        covers.remove(tripId, userId);
        return ResponseEntity.noContent().build();
    }
}
