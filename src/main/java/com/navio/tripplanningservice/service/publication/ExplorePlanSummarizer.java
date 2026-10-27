package com.navio.tripplanningservice.service.publication;

import com.navio.tripplanningservice.dto.publication.ExplorePlanSummary;
import com.navio.tripplanningservice.dto.publication.PublicDayDto;
import com.navio.tripplanningservice.dto.publication.PublicItemDto;
import com.navio.tripplanningservice.dto.publication.PublicPlanSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reduces a published snapshot to an Explore card.
 *
 * <p>Input is the already sanitised snapshot, never the live trip, so this class
 * cannot widen what is public — it can only say less. A pure function: no
 * repositories, no clock.
 */
@Component
public class ExplorePlanSummarizer {

    static final int MAX_HIGHLIGHTS = 3;

    public ExplorePlanSummary summarize(
            String token,
            PublicPlanSnapshot plan,
            String authorName,
            Instant listedAt,
            Instant updatedAt) {
        return summarize(token, plan, authorName, listedAt, updatedAt, 0, 0);
    }

    public ExplorePlanSummary summarize(String token, PublicPlanSnapshot plan, String authorName,
            Instant listedAt, Instant updatedAt, long viewCount, long copyCount) {
        String firstPlaceId = null;
        String imagePlaceId = null;
        List<String> highlights = new ArrayList<>();
        List<ExplorePlanSummary.ExploreDayShape> days = new ArrayList<>();
        String coverImageUrl = null;
        int placeCount = 0;
        int chargerCount = 0;

        for (PublicDayDto day : nullSafe(plan.days())) {
            if (day == null) {
                continue;
            }
            List<String> stops = new ArrayList<>();
            for (PublicItemDto item : nullSafe(day.items())) {
                if (item == null || item.type() == null) {
                    continue;
                }
                switch (item.type()) {
                    case "charger" -> {
                        chargerCount++;
                        stops.add("charger");
                    }
                    case "place" -> {
                        placeCount++;
                        stops.add("place");
                        if (firstPlaceId == null && item.placeId() != null) firstPlaceId = item.placeId();
                        if (coverImageUrl == null && item.imageUrl() != null) {
                            coverImageUrl = item.imageUrl();
                            imagePlaceId = item.placeId();
                        }
                        if (highlights.size() < MAX_HIGHLIGHTS
                                && item.name() != null
                                && !item.name().isBlank()
                                && !highlights.contains(item.name())) {
                            highlights.add(item.name());
                        }
                    }
                    default -> {
                        // Notes and checklists are content, not stops on the route.
                    }
                }
            }
            days.add(new ExplorePlanSummary.ExploreDayShape(List.copyOf(stops)));
        }

        return new ExplorePlanSummary(
                token,
                plan.title(),
                authorName,
                plan.destinationCity(),
                plan.destinationCountry(),
                plan.dayCount(),
                placeCount,
                chargerCount,
                coverImageUrl,
                List.copyOf(highlights),
                List.copyOf(days),
                listedAt,
                updatedAt,
                coverImageUrl != null ? imagePlaceId : firstPlaceId,
                viewCount, copyCount);
    }

    private <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
