package com.navio.tripplanningservice.service.publication;

import com.navio.tripplanningservice.model.OutboxEvent;
import com.navio.tripplanningservice.model.TripPublication;
import com.navio.tripplanningservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stages {@code trip.events.v1} events for publication changes followers care about.
 *
 * <p>Only one change is announced: a plan <em>becoming</em> publicly discoverable
 * under its owner's name, meaning active, listed on Explore and carrying a byline.
 * Private edits, autosaves, link-only shares, anonymous listings, unlisting and
 * updates to a plan that stays listed are silent. Every transition into the
 * announceable state stages an event, including relisting the same link; the
 * consumer deduplicates per share token, so followers hear about one link once.
 * A re-publication after Stop sharing gets a new link and is a new announcement.
 *
 * <p>{@link Propagation#MANDATORY}: an event is only ever written inside the
 * transaction that makes the change, so a rolled-back publish announces nothing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublicationEvents {

    public static final String AGGREGATE_PUBLICATION = "TRIP_PUBLICATION";
    public static final String EVENT_SHARED_PLAN_LISTED = "SharedPlanListed.v1";
    public static final int PAYLOAD_VERSION = 1;

    private final OutboxEventRepository outbox;

    /** What a publication looked like before a change, captured before mutating it. */
    public record Before(boolean announceable, String token) {
        public static final Before NONE = new Before(false, null);

        public static Before of(TripPublication publication) {
            return publication == null ? NONE : new Before(PublicationEvents.announceable(publication), publication.getToken());
        }
    }

    public static boolean announceable(TripPublication publication) {
        return publication.isListed()
                && publication.getAuthorDisplayName() != null
                && !publication.getAuthorDisplayName().isBlank();
    }

    /**
     * Stages a {@code SharedPlanListed.v1} event when {@code after} newly became
     * announceable. Call after the publication row is flushed so its id is set.
     *
     * @return true when an event was staged
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordChange(Before before, TripPublication after, UUID ownerId) {
        if (!announceable(after)) return false;
        boolean sameLink = before.announceable() && after.getToken().equals(before.token());
        if (sameLink) return false;

        Instant occurredAt = after.getListedAt() != null ? after.getListedAt() : Instant.now();
        // Identifiers only: the consumer reads titles and visibility from trip
        // planning at delivery time, so the broker never holds plan content.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", PAYLOAD_VERSION);
        payload.put("publicationId", after.getId().toString());
        payload.put("tripId", after.getTripId().toString());
        payload.put("ownerId", ownerId.toString());
        payload.put("token", after.getToken());
        payload.put("revision", after.getRevision());
        payload.put("listedAt", occurredAt.toString());
        String trace = MDC.get("traceId");
        if (trace != null && !trace.isBlank()) payload.put("traceId", trace);

        OutboxEvent staged = outbox.save(OutboxEvent.builder()
                .aggregateType(AGGREGATE_PUBLICATION)
                .aggregateId(after.getId())
                .eventType(EVENT_SHARED_PLAN_LISTED)
                .partitionKey(after.getId().toString())
                .payload(payload)
                .build());
        log.info("Staged {} {} for publication {}", EVENT_SHARED_PLAN_LISTED, staged.getId(), after.getId());
        return true;
    }
}
