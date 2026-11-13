package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.dto.TripCoverResponse;
import com.navio.tripplanningservice.media.PictureException;
import com.navio.tripplanningservice.media.PictureValidator;
import com.navio.tripplanningservice.repository.TripCoverRepository;
import com.navio.tripplanningservice.repository.TripRepository;
import com.navio.tripplanningservice.service.TripService.TripNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * The owner's banner photo for one trip. Only the trip owner can upload, read
 * or remove it; anyone else gets the same 404 as for a trip that does not exist.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripCoverService {

    private final TripRepository trips;
    private final TripCoverRepository covers;

    @Transactional
    public TripCoverResponse upload(UUID tripId, UUID userId, MultipartFile file) {
        requireOwned(tripId, userId);
        var picture = PictureValidator.validate(file);
        UUID coverId = UUID.randomUUID();
        covers.save(tripId, coverId, picture);
        return new TripCoverResponse(tripId, coverId);
    }

    @Transactional
    public void remove(UUID tripId, UUID userId) {
        requireOwned(tripId, userId);
        covers.delete(tripId);
    }

    public PictureValidator.Picture read(UUID tripId, UUID userId) {
        return covers.findOwned(tripId, userId)
                .orElseThrow(() -> new PictureException(HttpStatus.NOT_FOUND, "Trip cover not found"));
    }

    private void requireOwned(UUID tripId, UUID userId) {
        if (trips.findByIdAndUserId(tripId, userId).isEmpty()) throw new TripNotFoundException(tripId);
    }
}
