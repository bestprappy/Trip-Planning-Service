package com.navio.tripplanningservice.service.publication;

import com.navio.tripplanningservice.dto.publication.ExplorePlanSummary;
import com.navio.tripplanningservice.model.TripPublication;
import com.navio.tripplanningservice.repository.TripPublicationRepository;
import com.navio.tripplanningservice.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Shared plans on a traveler's public profile.
 *
 * <p>Only plans the owner listed on Explore <em>under their own name</em> appear:
 * a link-only share stays between the owner and the people they gave it to, and
 * an anonymous listing is never attributed. The cards are the same frozen,
 * sanitised Explore summaries, so a profile can show nothing Explore would not.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProfilePlanService {

    /** Most plans an owner can feature on their profile at once. */
    public static final int MAX_PINS = 3;

    private final TripPublicationRepository publications;
    private final TripRepository trips;
    private final TripPublicationService publicationService;

    public record ProfilePlanSummary(ExplorePlanSummary plan, boolean pinned) { }

    public Page<ProfilePlanSummary> listedBy(UUID owner, Pageable pageable) {
        Page<TripPublication> rows = publications.findListedByOwner(owner, PlanPublicationSanitizer.SANITIZER_VERSION, pageable);
        Page<ExplorePlanSummary> summaries = publicationService.summarize(rows, pageable);
        var pinnedTokens = rows.getContent().stream().filter(row -> row.getProfilePinnedAt() != null)
                .map(TripPublication::getToken).toList();
        var content = new ArrayList<ProfilePlanSummary>(summaries.getNumberOfElements());
        for (ExplorePlanSummary summary : summaries.getContent()) {
            content.add(new ProfilePlanSummary(summary, pinnedTokens.contains(summary.token())));
        }
        return new PageImpl<>(content, pageable, rows.getTotalElements());
    }

    /** The account behind a byline, only for its profile link. */
    public record PlanAuthor(UUID accountId) { }

    /**
     * Who published a plan, but only when the owner put their name on an Explore
     * listing: the same fact {@link #listedBy} already exposes in the other
     * direction. Link-only, anonymous, revoked and unknown plans all answer 404.
     */
    public PlanAuthor authorOf(String token) {
        return publications.findByTokenAndStatus(token, TripPublication.PublicationStatus.ACTIVE)
                .filter(TripPublication::isListed)
                .filter(row -> row.getAuthorDisplayName() != null)
                .filter(row -> row.getSanitizerVersion() >= PlanPublicationSanitizer.SANITIZER_VERSION)
                .flatMap(row -> trips.findById(row.getTripId()))
                .map(trip -> new PlanAuthor(trip.getUserId()))
                .orElseThrow(TripPublicationService.SharedPlanUnavailableException::new);
    }

    /**
     * Features or un-features one of the caller's listed plans. Someone else's
     * plan answers exactly like an unknown link, so pinning cannot probe ownership.
     */
    @Transactional
    public ProfilePlanSummary pin(String token, UUID owner, boolean pinned) {
        TripPublication publication = publications.findByTokenAndStatus(token, TripPublication.PublicationStatus.ACTIVE)
                .filter(row -> trips.findByIdAndUserId(row.getTripId(), owner).isPresent())
                .orElseThrow(TripPublicationService.SharedPlanUnavailableException::new);
        if (pinned) {
            if (!publication.isListed() || publication.getAuthorDisplayName() == null) {
                throw new ProfilePinException("Only plans listed on Explore under your name can be pinned");
            }
            if (publication.getProfilePinnedAt() == null && publications.countPinnedByOwner(owner) >= MAX_PINS) {
                throw new ProfilePinException("You can pin up to 3 plans; unpin one first");
            }
            if (publication.getProfilePinnedAt() == null) publication.setProfilePinnedAt(Instant.now());
        } else {
            publication.setProfilePinnedAt(null);
        }
        publications.saveAndFlush(publication);
        log.info("{} plan publication {} on its owner's profile", pinned ? "Pinned" : "Unpinned", publication.getId());
        return new ProfilePlanSummary(null, pinned);
    }

    /** The pin cannot be applied in the plan's current state. */
    public static class ProfilePinException extends RuntimeException {
        public ProfilePinException(String message) {
            super(message);
        }
    }
}
