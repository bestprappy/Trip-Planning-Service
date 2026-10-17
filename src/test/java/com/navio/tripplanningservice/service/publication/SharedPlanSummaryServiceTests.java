package com.navio.tripplanningservice.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.navio.tripplanningservice.dto.publication.ExplorePlanSummary;
import com.navio.tripplanningservice.model.TripPublication;
import com.navio.tripplanningservice.repository.TripPublicationRepository;
import com.navio.tripplanningservice.service.publication.TripPublicationService.SharedPlanUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharedPlanSummaryServiceTests {

    private static final String TOKEN = "tok-3f9a2b";
    private static final Instant LISTED_AT = Instant.parse("2026-09-20T10:00:00Z");

    @Mock TripPublicationRepository publicationRepository;

    private SharedPlanSummaryService service;

    @BeforeEach
    void setUp() {
        service = new SharedPlanSummaryService(
                publicationRepository,
                new ExplorePlanSummarizer(),
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void summarisesAListedPlanFromItsFrozenSnapshot() {
        stubPublication(publication(true, PlanPublicationSanitizer.SANITIZER_VERSION, snapshotJson()));

        ExplorePlanSummary summary = service.summarizeListedPlan(TOKEN);

        assertThat(summary.token()).isEqualTo(TOKEN);
        assertThat(summary.title()).isEqualTo("Songkran road trip");
        assertThat(summary.authorName()).isEqualTo("Kanya S.");
        assertThat(summary.destinationCity()).isEqualTo("Bangkok");
        assertThat(summary.placeCount()).isEqualTo(1);
        assertThat(summary.chargerCount()).isEqualTo(1);
        assertThat(summary.highlights()).containsExactly("Wat Arun");
        assertThat(summary.listedAt()).isEqualTo(LISTED_AT);
    }

    @Test
    void lookingUpACardDoesNotCountAsAView() {
        stubPublication(publication(true, PlanPublicationSanitizer.SANITIZER_VERSION, snapshotJson()));

        service.summarizeListedPlan(TOKEN);

        verify(publicationRepository, never()).incrementViewCount(any());
    }

    @Test
    void refusesAPublishedPlanThatIsNotListedOnExplore() {
        stubPublication(publication(false, PlanPublicationSanitizer.SANITIZER_VERSION, snapshotJson()));

        assertThatThrownBy(() -> service.summarizeListedPlan(TOKEN))
                .isInstanceOf(SharedPlanUnavailableException.class);
    }

    @Test
    void refusesARevokedOrUnknownToken() {
        when(publicationRepository.findByTokenAndStatus(TOKEN, TripPublication.PublicationStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.summarizeListedPlan(TOKEN))
                .isInstanceOf(SharedPlanUnavailableException.class);
    }

    @Test
    void refusesASnapshotFrozenByASupersededSanitizer() {
        stubPublication(publication(true, PlanPublicationSanitizer.SANITIZER_VERSION - 1, snapshotJson()));

        assertThatThrownBy(() -> service.summarizeListedPlan(TOKEN))
                .isInstanceOf(SharedPlanUnavailableException.class);
    }

    @Test
    void refusesABlankTokenWithoutQueryingStorage() {
        assertThatThrownBy(() -> service.summarizeListedPlan(" "))
                .isInstanceOf(SharedPlanUnavailableException.class);
        verifyNoInteractions(publicationRepository);
    }

    @Test
    void reportsAnUnreadableSnapshotAsUnavailable() {
        stubPublication(publication(true, PlanPublicationSanitizer.SANITIZER_VERSION, "{not json"));

        assertThatThrownBy(() -> service.summarizeListedPlan(TOKEN))
                .isInstanceOf(SharedPlanUnavailableException.class);
    }

    private void stubPublication(TripPublication publication) {
        when(publicationRepository.findByTokenAndStatus(TOKEN, TripPublication.PublicationStatus.ACTIVE))
                .thenReturn(Optional.of(publication));
    }

    private TripPublication publication(boolean listed, int sanitizerVersion, String snapshot) {
        return TripPublication.builder()
                .token(TOKEN)
                .status(TripPublication.PublicationStatus.ACTIVE)
                .sanitizerVersion(sanitizerVersion)
                .listedInExplore(listed)
                .listedAt(listed ? LISTED_AT : null)
                .authorDisplayName("Kanya S.")
                .snapshot(snapshot)
                .build();
    }

    private String snapshotJson() {
        return """
                {"sanitizerVersion": %d, "title": "Songkran road trip",
                 "destinationCity": "Bangkok", "destinationCountry": "Thailand", "dayCount": 1,
                 "days": [{"label": "Day 1", "items": [
                   {"type": "place", "name": "Wat Arun"},
                   {"type": "charger", "name": "EA Anywhere"}]}]}
                """.formatted(PlanPublicationSanitizer.SANITIZER_VERSION);
    }
}
