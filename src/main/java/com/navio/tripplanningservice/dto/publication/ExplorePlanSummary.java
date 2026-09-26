package com.navio.tripplanningservice.dto.publication;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * One card on the Explore page.
 *
 * <p>Derived entirely from the frozen, sanitised {@link PublicPlanSnapshot} — never
 * from the live trip — so a listing can say nothing the shared page itself would
 * not. The token is included because a listed plan is public by the owner's
 * choice; it is the same link the owner would hand out.
 *
 * @param coverImageUrl the first place photo in the itinerary, already restricted
 *                      to http(s) by the sanitiser; null when the plan has none
 * @param highlights    up to three place names, in itinerary order, for search and
 *                      for the card's summary line
 * @param days          the shape of each day, used to draw the route strip
 * @param authorName    the owner's byline, or null to show "a Navio traveler"
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExplorePlanSummary(
        String token,
        String title,
        String authorName,
        String destinationCity,
        String destinationCountry,
        int dayCount,
        int placeCount,
        int chargerCount,
        String coverImageUrl,
        List<String> highlights,
        List<ExploreDayShape> days,
        Instant listedAt,
        Instant updatedAt
) {
    /**
     * @param stops the kind of each stop in order: {@code place} or {@code charger}.
     *              Notes and checklists are not stops and are not counted
     */
    public record ExploreDayShape(List<String> stops) {
    }
}
