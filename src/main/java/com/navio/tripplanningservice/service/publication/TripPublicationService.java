package com.navio.tripplanningservice.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.dto.PlannerSnapshotResponse;
import com.navio.tripplanningservice.dto.publication.ExplorePlanSummary;
import com.navio.tripplanningservice.dto.publication.PublicPlanSnapshot;
import com.navio.tripplanningservice.dto.publication.PublicationOptions;
import com.navio.tripplanningservice.dto.publication.PublicationResponse;
import com.navio.tripplanningservice.dto.publication.PublishPlanRequest;
import com.navio.tripplanningservice.dto.publication.SharedPlanResponse;
import com.navio.tripplanningservice.model.Trip;
import com.navio.tripplanningservice.model.TripPublication;
import com.navio.tripplanningservice.repository.TripPublicationRepository;
import com.navio.tripplanningservice.repository.TripRepository;
import com.navio.tripplanningservice.service.PlannerService;
import com.navio.tripplanningservice.service.TripService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the lifecycle of a trip's published link.
 *
 * <p>Every management method takes the acting user and resolves the trip through
 * {@link TripRepository#findByIdAndUserId}, so a non-owner gets the same
 * {@code 404} as someone asking about a trip that does not exist — the
 * authorization check and the existence check are the same query, which is why
 * there is no path that forgets one.
 *
 * <p>The anonymous side is {@link #readSharedPlan(String)}, which takes no user
 * at all. It can reach exactly one row, by token, and returns only the frozen
 * sanitised snapshot.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripPublicationService {

    private final TripRepository tripRepository;
    private final TripPublicationRepository publicationRepository;
    private final PlannerService plannerService;
    private final PlanPublicationSanitizer sanitizer;
    private final ShareTokenGenerator tokenGenerator;
    private final ObjectMapper objectMapper;
    private final ExplorePlanSummarizer summarizer;

    public PublicationResponse getPublication(UUID tripId, UUID userId) {
        Trip trip = requireOwnedTrip(tripId, userId);
        return publicationRepository.findByTripId(tripId)
                .filter(TripPublication::isActive)
                .map(publication -> describe(publication, trip))
                .orElseGet(PublicationResponse::notPublished);
    }

    /**
     * The exact object publication would freeze, without freezing it.
     *
     * <p>Shares one code path with {@link #publish}: both call
     * {@link PlanPublicationSanitizer#sanitize}, on the same saved trip. A
     * preview that used different code would be theatre.
     */
    public PublicPlanSnapshot preview(UUID tripId, UUID userId, PublicationOptions options) {
        Trip trip = requireOwnedTrip(tripId, userId);
        PlannerSnapshotResponse snapshot = plannerService.getPlannerSnapshot(tripId, userId);
        return sanitizer.sanitize(trip, snapshot, options);
    }

    @Transactional
    public PublicationResponse publish(UUID tripId, UUID userId, PublishPlanRequest request) {
        Trip trip = requireOwnedTrip(tripId, userId);

        // The owner published what they reviewed, or nothing. A trip that moved
        // between preview and publish means the snapshot they approved is not
        // the snapshot this call would freeze.
        if (!Objects.equals(trip.getVersion(), request.expectedTripVersion())) {
            throw new PublicationConflictException(
                    "This plan changed since you opened the preview");
        }

        Optional<TripPublication> existing = publicationRepository.findByTripId(tripId);
        requireExpectedRevision(existing, request.expectedRevision());

        PublicationOptions options = request.safeOptions();
        PublicPlanSnapshot sanitized = sanitizer.sanitize(
                trip, plannerService.getPlannerSnapshot(tripId, userId), options);

        TripPublication publication = existing.orElseGet(() -> TripPublication.builder()
                .tripId(tripId)
                .revision(0)
                .build());

        // A re-publish after Stop sharing mints a new token rather than reviving
        // the old one, so a link the owner retired never starts working again.
        if (!publication.isActive()) {
            publication.setToken(tokenGenerator.generate());
            publication.setPublishedAt(Instant.now());
            publication.setRevokedAt(null);
        }
        publication.setStatus(TripPublication.PublicationStatus.ACTIVE);
        publication.setSnapshot(serialize(sanitized));
        publication.setSanitizerVersion(sanitized.sanitizerVersion());
        publication.setSourceTripVersion(trip.getVersion());
        publication.setIncludeDates(options.includeDates());
        publication.setIncludeNotes(options.includeNotes());
        publication.setIncludeBudget(options.includeBudget());
        // The request states the whole desired state, so an Update that omits
        // the flag unlists rather than silently keeping a public listing.
        publication.applyListing(request.safeListInExplore(), Instant.now());
        // The byline is part of what the owner reviewed in the dialog, so it is
        // replaced with each publish, including by null when they sent none.
        publication.setAuthorDisplayName(sanitizer.sanitizeAuthorName(request.authorDisplayName()));
        publication.setRevision(publication.getRevision() + 1);

        TripPublication saved = publicationRepository.saveAndFlush(publication);
        log.info("Published plan for trip {} at revision {}", tripId, saved.getRevision());
        return describe(saved, trip);
    }

    /**
     * Stops sharing. Idempotent: an owner clicking twice, or revoking something
     * already revoked, succeeds quietly rather than erroring on the safe outcome.
     *
     * <p>The token is cleared, not flagged. The snapshot stays so the row can
     * record that this trip was once published, but nothing points at it.
     */
    @Transactional
    public void revoke(UUID tripId, UUID userId) {
        requireOwnedTrip(tripId, userId);
        publicationRepository.findByTripId(tripId)
                .filter(TripPublication::isActive)
                .ifPresent(publication -> {
                    publication.setStatus(TripPublication.PublicationStatus.REVOKED);
                    publication.setToken(null);
                    // A stopped link is never discoverable; the database also
                    // refuses a REVOKED row that is still listed.
                    publication.applyListing(false, Instant.now());
                    publication.setRevokedAt(Instant.now());
                    publicationRepository.saveAndFlush(publication);
                    log.info("Stopped sharing trip {}", tripId);
                });
    }

    /**
     * The anonymous read.
     *
     * <p>Every failure — no such token, revoked, trip deleted, snapshot frozen by
     * a superseded sanitiser — raises the same exception with the same message.
     * A caller must not be able to tell "this link never existed" from "this link
     * was withdrawn", because the second confirms a trip exists and that someone
     * decided to stop showing it.
     */
    public SharedPlanResponse readSharedPlan(String token) {
        if (token == null || token.isBlank()) {
            throw new SharedPlanUnavailableException();
        }
        TripPublication publication = publicationRepository
                .findByTokenAndStatus(token, TripPublication.PublicationStatus.ACTIVE)
                .orElseThrow(SharedPlanUnavailableException::new);

        // A snapshot frozen under older redaction rules is not served under the
        // new ones. Re-publishing regenerates it; until then the link is dead.
        if (publication.getSanitizerVersion() == null
                || publication.getSanitizerVersion() < PlanPublicationSanitizer.SANITIZER_VERSION) {
            log.warn("Refused a shared plan frozen by sanitizer v{}; current is v{}",
                    publication.getSanitizerVersion(), PlanPublicationSanitizer.SANITIZER_VERSION);
            throw new SharedPlanUnavailableException();
        }

        return new SharedPlanResponse(
                deserialize(publication),
                publication.getPublishedAt(),
                publication.isListed(),
                publication.getAuthorDisplayName());
    }

    /**
     * Lists or unlists a published plan on Explore, immediately and without
     * re-publishing its content.
     */
    @Transactional
    public PublicationResponse updateExploreListing(
            UUID tripId, UUID userId, boolean listed, String authorDisplayName) {
        Trip trip = requireOwnedTrip(tripId, userId);
        TripPublication publication = publicationRepository.findByTripId(tripId)
                .filter(TripPublication::isActive)
                .orElseThrow(() -> new PublicationConflictException(
                        "Publish this plan before listing it on Explore"));

        publication.applyListing(listed, Instant.now());
        if (authorDisplayName != null) {
            // Listing is when a byline matters most, so the owner's current name
            // is taken here too; omitting it keeps the one already published.
            publication.setAuthorDisplayName(sanitizer.sanitizeAuthorName(authorDisplayName));
        }
        TripPublication saved = publicationRepository.saveAndFlush(publication);
        log.info("{} trip {} on Explore", listed ? "Listed" : "Unlisted", tripId);
        return describe(saved, trip);
    }

    /**
     * The public Explore feed: plans their owners chose to list, newest first.
     *
     * <p>Built from each row's frozen snapshot, so a card shows exactly what the
     * shared page would. A row whose snapshot cannot be read is left out of the
     * page rather than failing the whole feed for everyone.
     */
    public Page<ExplorePlanSummary> listExplorePlans(Pageable pageable) {
        Page<TripPublication> rows = publicationRepository.findListedInExplore(
                PlanPublicationSanitizer.SANITIZER_VERSION, pageable);

        List<ExplorePlanSummary> summaries = new ArrayList<>(rows.getNumberOfElements());
        for (TripPublication row : rows.getContent()) {
            try {
                PublicPlanSnapshot plan = objectMapper.readValue(row.getSnapshot(), PublicPlanSnapshot.class);
                summaries.add(summarizer.summarize(
                        row.getToken(), plan, row.getAuthorDisplayName(), row.getListedAt(), row.getUpdatedAt()));
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                log.error("Stored snapshot for publication {} is unreadable; left out of Explore",
                        row.getId(), exception);
            }
        }
        return new PageImpl<>(summaries, pageable, rows.getTotalElements());
    }

    private void requireExpectedRevision(Optional<TripPublication> existing, Integer expectedRevision) {
        boolean active = existing.filter(TripPublication::isActive).isPresent();
        if (!active) {
            // Nothing published yet; the client's revision, if any, is stale.
            return;
        }
        int current = existing.get().getRevision();
        if (expectedRevision == null) {
            throw new PublicationConflictException("This plan is already published");
        }
        if (expectedRevision != current) {
            throw new PublicationConflictException(
                    "This link was updated somewhere else");
        }
    }

    private PublicationResponse describe(TripPublication publication, Trip trip) {
        return new PublicationResponse(
                true,
                publication.getToken(),
                new PublicationOptions(
                        Boolean.TRUE.equals(publication.getIncludeDates()),
                        Boolean.TRUE.equals(publication.getIncludeNotes()),
                        Boolean.TRUE.equals(publication.getIncludeBudget())),
                publication.getRevision(),
                publication.getPublishedAt(),
                publication.getUpdatedAt(),
                !Objects.equals(publication.getSourceTripVersion(), trip.getVersion()),
                publication.getSanitizerVersion() == null
                        || publication.getSanitizerVersion() < PlanPublicationSanitizer.SANITIZER_VERSION,
                publication.isListed(),
                publication.getAuthorDisplayName());
    }

    private Trip requireOwnedTrip(UUID tripId, UUID userId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripService.TripNotFoundException(tripId));
    }

    private String serialize(PublicPlanSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Published snapshot could not be serialized", exception);
        }
    }

    private PublicPlanSnapshot deserialize(TripPublication publication) {
        try {
            return objectMapper.readValue(publication.getSnapshot(), PublicPlanSnapshot.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            // Do not fall back to re-projecting from the live trip: that would
            // silently publish current content under a link the owner froze.
            log.error("Stored snapshot for publication {} is unreadable", publication.getId(), exception);
            throw new SharedPlanUnavailableException();
        }
    }

    /** Raised when a publish would overwrite something the owner has not seen. */
    public static class PublicationConflictException extends RuntimeException {
        public PublicationConflictException(String message) {
            super(message);
        }
    }

    /**
     * The single failure of the anonymous read path.
     *
     * <p>One exception for every cause, carrying no detail, so the response is
     * identical whichever one occurred.
     */
    public static class SharedPlanUnavailableException extends RuntimeException {
        public SharedPlanUnavailableException() {
            super("This shared plan is no longer available");
        }
    }
}
