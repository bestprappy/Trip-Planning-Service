package com.navio.tripplanningservice.service.publication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.dto.publication.ExplorePlanSummary;
import com.navio.tripplanningservice.dto.publication.PublicPlanSnapshot;
import com.navio.tripplanningservice.model.TripPublication;
import com.navio.tripplanningservice.repository.TripPublicationRepository;
import com.navio.tripplanningservice.service.publication.TripPublicationService.SharedPlanUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One Explore card, looked up by token, for places that reference a listed plan
 * (a community post attachment) rather than browse the feed.
 *
 * <p>Answers only for plans listed on Explore right now. A listed plan's token
 * is already public in the feed, so this widens nothing; an unlisted link is
 * reported exactly like a missing one, so a token pasted somewhere public cannot
 * be used to confirm that a private plan exists. Unlike opening the plan, a
 * lookup does not count as a view, so cards rendered in a feed cannot inflate
 * the trending order.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SharedPlanSummaryService {

    private final TripPublicationRepository publicationRepository;
    private final ExplorePlanSummarizer summarizer;
    private final ObjectMapper objectMapper;

    public ExplorePlanSummary summarizeListedPlan(String token) {
        if (token == null || token.isBlank()) {
            throw new SharedPlanUnavailableException();
        }
        TripPublication publication = publicationRepository
                .findByTokenAndStatus(token, TripPublication.PublicationStatus.ACTIVE)
                .filter(TripPublication::isListed)
                // Same rule as the shared page: a snapshot frozen under older
                // redaction rules is not served until it is re-published.
                .filter(row -> row.getSanitizerVersion() != null
                        && row.getSanitizerVersion() >= PlanPublicationSanitizer.SANITIZER_VERSION)
                .orElseThrow(SharedPlanUnavailableException::new);

        PublicPlanSnapshot snapshot;
        try {
            snapshot = objectMapper.readValue(publication.getSnapshot(), PublicPlanSnapshot.class);
        } catch (JsonProcessingException exception) {
            log.error("Stored snapshot for publication {} is unreadable", publication.getId(), exception);
            throw new SharedPlanUnavailableException();
        }
        return summarizer.summarize(
                publication.getToken(),
                snapshot,
                publication.getAuthorDisplayName(),
                publication.getListedAt(),
                publication.getUpdatedAt());
    }
}
