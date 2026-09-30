package com.navio.tripplanningservice.service.publication;

import com.navio.tripplanningservice.dto.publication.ExplorePlanSummary;
import com.navio.tripplanningservice.dto.publication.PublicChargerDto;
import com.navio.tripplanningservice.dto.publication.PublicDayDto;
import com.navio.tripplanningservice.dto.publication.PublicItemDto;
import com.navio.tripplanningservice.dto.publication.PublicPlanSnapshot;
import com.navio.tripplanningservice.dto.publication.PublicationOptions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExplorePlanSummarizerTests {

    private static final Instant LISTED = Instant.parse("2026-09-20T10:00:00Z");

    private final ExplorePlanSummarizer summarizer = new ExplorePlanSummarizer();

    @Test
    void drawsEachDayAsItsOrderedStopsSoTheRouteStripMatchesTheItinerary() {
        ExplorePlanSummary summary = summarize(
                day(place("Wat Arun", null), charger("PTT EV Station"), place("Jodd Fairs", null)),
                day(place("Khao Yai", null)));

        assertThat(summary.days()).extracting(ExplorePlanSummary.ExploreDayShape::stops)
                .containsExactly(List.of("place", "charger", "place"), List.of("place"));
        assertThat(summary.placeCount()).isEqualTo(3);
        assertThat(summary.chargerCount()).isEqualTo(1);
    }

    @Test
    void notesAndChecklistsAreNotStopsOnTheRoute() {
        PublicItemDto note = new PublicItemDto(
                "note", null, null, null, null, null, null, null, null, null, "Bring cash", null, null, null);
        ExplorePlanSummary summary = summarize(day(place("Wat Arun", null), note));

        assertThat(summary.days().getFirst().stops()).containsExactly("place");
    }

    @Test
    void usesTheFirstPlacePhotoAsTheCover() {
        ExplorePlanSummary summary = summarize(
                day(place("No photo", null), place("Wat Arun", "https://img.example/arun.jpg"),
                        place("Later", "https://img.example/later.jpg")));

        assertThat(summary.coverImageUrl()).isEqualTo("https://img.example/arun.jpg");
    }

    @Test
    void keepsAtMostThreeDistinctHighlightsInItineraryOrder() {
        ExplorePlanSummary summary = summarize(
                day(place("Wat Arun", null), place("Wat Arun", null), place("Jodd Fairs", null)),
                day(place("Khao Yai", null), place("Pai", null)));

        assertThat(summary.highlights()).containsExactly("Wat Arun", "Jodd Fairs", "Khao Yai");
    }

    @Test
    void carriesTheBylineThroughToTheCard() {
        assertThat(summarize(day(place("Wat Arun", null))).authorName()).isEqualTo("Kanya S.");
    }

    @Test
    void normalisesABylineToOneShortLineOrNothing() {
        PlanPublicationSanitizer sanitizer = new PlanPublicationSanitizer();

        assertThat(sanitizer.sanitizeAuthorName("  Kanya \n\t S.\u0000 ")).isEqualTo("Kanya S.");
        assertThat(sanitizer.sanitizeAuthorName("   ")).isNull();
        assertThat(sanitizer.sanitizeAuthorName(null)).isNull();
        assertThat(sanitizer.sanitizeAuthorName("x".repeat(500))).hasSize(PlanPublicationSanitizer.MAX_AUTHOR_NAME_LENGTH);
    }

    @Test
    void summarisesAnEmptyPlanWithoutFailing() {
        PublicPlanSnapshot empty = new PublicPlanSnapshot(
                1, "Empty", null, null, null, null, 0, null, null, PublicationOptions.none());

        ExplorePlanSummary summary = summarizer.summarize("tok", empty, null, LISTED, LISTED);

        assertThat(summary.days()).isEmpty();
        assertThat(summary.coverImageUrl()).isNull();
        assertThat(summary.highlights()).isEmpty();
    }

    private ExplorePlanSummary summarize(PublicDayDto... days) {
        PublicPlanSnapshot plan = new PublicPlanSnapshot(
                1, "Songkran road trip", "Bangkok", "Thailand", null, null, days.length,
                List.of(days), null, PublicationOptions.none());
        return summarizer.summarize("tok-listed", plan, "Kanya S.", LISTED, LISTED);
    }

    private PublicDayDto day(PublicItemDto... items) {
        return new PublicDayDto("Day", null, null, null, null, List.of(items));
    }

    private PublicItemDto place(String name, String imageUrl) {
        return new PublicItemDto(
                "place", name, null, imageUrl, null, null, null, null, null, null, null, null, null, null);
    }

    private PublicItemDto charger(String name) {
        return new PublicItemDto(
                "charger", name, null, null, null, null, null, null, null, null, null, null, null,
                new PublicChargerDto(List.of("CCS2"), 120.0, 4, null, null, null));
    }
}
