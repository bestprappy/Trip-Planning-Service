package com.navio.tripplanningservice.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.navio.tripplanningservice.dto.PlannerBlockDto;
import com.navio.tripplanningservice.dto.PlannerSnapshotResponse;
import com.navio.tripplanningservice.dto.publication.ExplorePlanSummary;
import com.navio.tripplanningservice.dto.publication.PublicationOptions;
import com.navio.tripplanningservice.dto.publication.PublicationResponse;
import com.navio.tripplanningservice.dto.publication.PublishPlanRequest;
import com.navio.tripplanningservice.dto.publication.SharedPlanResponse;
import com.navio.tripplanningservice.model.CurrencyCode;
import com.navio.tripplanningservice.model.Trip;
import com.navio.tripplanningservice.model.TripPublication;
import com.navio.tripplanningservice.model.TripVisibility;
import com.navio.tripplanningservice.repository.TripPublicationRepository;
import com.navio.tripplanningservice.repository.TripRepository;
import com.navio.tripplanningservice.service.PlannerService;
import com.navio.tripplanningservice.service.TripService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripPublicationServiceTests {

    private static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-00000000000a");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-4000-8000-00000000000b");
    private static final UUID TRIP = UUID.fromString("00000000-0000-4000-8000-0000000000c1");

    @Mock TripRepository tripRepository;
    @Mock TripPublicationRepository publicationRepository;
    @Mock PlannerService plannerService;
    @Mock ShareTokenGenerator tokenGenerator;

    private TripPublicationService service;

    @BeforeEach
    void setUp() {
        service = new TripPublicationService(
                tripRepository,
                publicationRepository,
                plannerService,
                new PlanPublicationSanitizer(),
                tokenGenerator,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                new ExplorePlanSummarizer());
    }

    // ---------------------------------------------------------- authorization

    @Test
    void aStrangerCannotReadTheOwnersPublicationState() {
        when(tripRepository.findByIdAndUserId(TRIP, STRANGER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublication(TRIP, STRANGER))
                .isInstanceOf(TripService.TripNotFoundException.class);

        verifyNoInteractions(publicationRepository);
    }

    @Test
    void aStrangerCannotPublishSomeoneElsesTrip() {
        when(tripRepository.findByIdAndUserId(TRIP, STRANGER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(TRIP, STRANGER, publishRequest(3L, null)))
                .isInstanceOf(TripService.TripNotFoundException.class);

        verify(publicationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(plannerService, tokenGenerator);
    }

    @Test
    void aStrangerCannotStopSomeoneElsesSharing() {
        when(tripRepository.findByIdAndUserId(TRIP, STRANGER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(TRIP, STRANGER))
                .isInstanceOf(TripService.TripNotFoundException.class);

        verify(publicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void aStrangerCannotPreviewSomeoneElsesPlan() {
        when(tripRepository.findByIdAndUserId(TRIP, STRANGER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview(TRIP, STRANGER, PublicationOptions.none()))
                .isInstanceOf(TripService.TripNotFoundException.class);

        verifyNoInteractions(plannerService);
    }

    // ------------------------------------------------------------- publishing

    @Test
    void publishingFreezesASanitizedSnapshotAndMintsAToken() {
        givenOwnedTripAtVersion(3L);
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.empty());
        when(tokenGenerator.generate()).thenReturn("tok-abc");
        when(publicationRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        PublicationResponse response = service.publish(TRIP, OWNER, publishRequest(3L, null));

        assertThat(response.published()).isTrue();
        assertThat(response.token()).isEqualTo("tok-abc");
        assertThat(response.revision()).isEqualTo(1);
        assertThat(response.hasUnpublishedChanges()).isFalse();
    }

    @Test
    void refusesToPublishASourceThatMovedSinceThePreview() {
        givenOwnedTripAtVersion(4L);

        assertThatThrownBy(() -> service.publish(TRIP, OWNER, publishRequest(3L, null)))
                .isInstanceOf(TripPublicationService.PublicationConflictException.class)
                .hasMessageContaining("changed since you opened the preview");

        verify(publicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesAnUpdateBuiltOnARevisionAnotherTabAlreadySuperseded() {
        givenOwnedTripAtVersion(3L);
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.of(activePublication(5)));

        assertThatThrownBy(() -> service.publish(TRIP, OWNER, publishRequest(3L, 4)))
                .isInstanceOf(TripPublicationService.PublicationConflictException.class)
                .hasMessageContaining("updated somewhere else");

        verify(publicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesAFirstPublishWhenALinkAlreadyExists() {
        givenOwnedTripAtVersion(3L);
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.of(activePublication(2)));

        assertThatThrownBy(() -> service.publish(TRIP, OWNER, publishRequest(3L, null)))
                .isInstanceOf(TripPublicationService.PublicationConflictException.class)
                .hasMessageContaining("already published");
    }

    @Test
    void anUpdateKeepsTheSameUrlSoForwardedLinksKeepWorking() {
        givenOwnedTripAtVersion(3L);
        TripPublication existing = activePublication(2);
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.of(existing));
        when(publicationRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        PublicationResponse response = service.publish(TRIP, OWNER, publishRequest(3L, 2));

        assertThat(response.token()).isEqualTo("tok-existing");
        assertThat(response.revision()).isEqualTo(3);
        verifyNoInteractions(tokenGenerator);
    }

    /**
     * The rule that makes "Stop sharing" mean something. Re-publishing after a
     * revocation must not restore the old URL, or every link the owner ever gave
     * out would come back to life.
     */
    @Test
    void republishingAfterStoppingMintsANewTokenRatherThanRevivingTheOldOne() {
        givenOwnedTripAtVersion(3L);
        TripPublication revoked = activePublication(2);
        revoked.setStatus(TripPublication.PublicationStatus.REVOKED);
        revoked.setToken(null);
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.of(revoked));
        when(tokenGenerator.generate()).thenReturn("tok-fresh");
        when(publicationRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        PublicationResponse response = service.publish(TRIP, OWNER, publishRequest(3L, null));

        assertThat(response.token()).isEqualTo("tok-fresh").isNotEqualTo("tok-existing");
        assertThat(response.published()).isTrue();
    }

    @Test
    void reportsUnpublishedChangesOnceTheSourceTripMovesOn() {
        Trip trip = trip(9L);
        when(tripRepository.findByIdAndUserId(TRIP, OWNER)).thenReturn(Optional.of(trip));
        TripPublication publication = activePublication(2);
        publication.setSourceTripVersion(7L);
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.of(publication));

        assertThat(service.getPublication(TRIP, OWNER).hasUnpublishedChanges()).isTrue();
    }

    // ------------------------------------------------------------- revocation

    @Test
    void stoppingSharingClearsTheTokenRatherThanOnlyFlaggingTheRow() {
        when(tripRepository.findByIdAndUserId(TRIP, OWNER)).thenReturn(Optional.of(trip(3L)));
        TripPublication publication = activePublication(2);
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.of(publication));
        when(publicationRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        service.revoke(TRIP, OWNER);

        assertThat(publication.getToken()).isNull();
        assertThat(publication.getStatus()).isEqualTo(TripPublication.PublicationStatus.REVOKED);
        assertThat(publication.isActive()).isFalse();
        assertThat(publication.getRevokedAt()).isNotNull();
    }

    @Test
    void stoppingSharingTwiceSucceedsQuietly() {
        when(tripRepository.findByIdAndUserId(TRIP, OWNER)).thenReturn(Optional.of(trip(3L)));
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.empty());

        service.revoke(TRIP, OWNER);

        verify(publicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void aTripWithNoLinkReportsItselfUnpublishedWithNoToken() {
        when(tripRepository.findByIdAndUserId(TRIP, OWNER)).thenReturn(Optional.of(trip(3L)));
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.empty());

        PublicationResponse response = service.getPublication(TRIP, OWNER);

        assertThat(response.published()).isFalse();
        assertThat(response.token()).isNull();
        assertThat(response.options()).isNull();
    }

    // --------------------------------------------------------- anonymous read

    @Test
    void readsTheFrozenSnapshotForAnActiveToken() {
        when(publicationRepository.findByTokenAndStatus("tok-existing", TripPublication.PublicationStatus.ACTIVE))
                .thenReturn(Optional.of(activePublication(2)));

        SharedPlanResponse response = service.readSharedPlan("tok-existing");

        assertThat(response.plan().title()).isEqualTo("Songkran road trip");
        assertThat(response.publishedAt()).isNotNull();
    }

    /**
     * A revoked link, an unknown token and a blank token must be indistinguishable
     * from outside. Each raises the same type with the same message, so the
     * handler cannot accidentally produce three different bodies.
     */
    @Test
    void everyDeadLinkFailsIdenticallySoProbesLearnNothing() {
        when(publicationRepository.findByTokenAndStatus(any(), any())).thenReturn(Optional.empty());

        String unknown = catchMessage(() -> service.readSharedPlan("tok-never-existed"));
        String blank = catchMessage(() -> service.readSharedPlan(""));
        String nullToken = catchMessage(() -> service.readSharedPlan(null));

        assertThat(unknown).isEqualTo("This shared plan is no longer available");
        assertThat(blank).isEqualTo(unknown);
        assertThat(nullToken).isEqualTo(unknown);
    }

    /**
     * ACTIVE is part of the lookup, not a check performed on the row afterwards.
     * A revoked row is therefore unreachable even if it somehow kept a token —
     * the query never selects it, so no later branch has to remember to reject it.
     */
    @Test
    void scopesTheAnonymousLookupToActiveRowsInTheQueryItself() {
        when(publicationRepository.findByTokenAndStatus(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.readSharedPlan("tok-existing"))
                .isInstanceOf(TripPublicationService.SharedPlanUnavailableException.class);

        verify(publicationRepository)
                .findByTokenAndStatus("tok-existing", TripPublication.PublicationStatus.ACTIVE);
    }

    /**
     * The gap a frozen snapshot creates: a redaction fix does not reach rows
     * written before it. Rather than serve one under superseded rules, the link
     * goes dead until the owner re-publishes.
     */
    @Test
    void refusesASnapshotFrozenByASupersededSanitizer() {
        TripPublication stale = activePublication(2);
        stale.setSanitizerVersion(PlanPublicationSanitizer.SANITIZER_VERSION - 1);
        when(publicationRepository.findByTokenAndStatus("tok-existing", TripPublication.PublicationStatus.ACTIVE))
                .thenReturn(Optional.of(stale));

        assertThatThrownBy(() -> service.readSharedPlan("tok-existing"))
                .isInstanceOf(TripPublicationService.SharedPlanUnavailableException.class);
    }

    @Test
    void tellsTheOwnerTheirLinkIsStaleSoTheyCanRepublish() {
        when(tripRepository.findByIdAndUserId(TRIP, OWNER)).thenReturn(Optional.of(trip(3L)));
        TripPublication stale = activePublication(2);
        stale.setSanitizerVersion(PlanPublicationSanitizer.SANITIZER_VERSION - 1);
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.of(stale));

        assertThat(service.getPublication(TRIP, OWNER).staleSanitizer()).isTrue();
    }

    @Test
    void doesNotFallBackToLiveContentWhenAStoredSnapshotIsUnreadable() {
        TripPublication corrupt = activePublication(2);
        corrupt.setSnapshot("{ not json");
        when(publicationRepository.findByTokenAndStatus("tok-existing", TripPublication.PublicationStatus.ACTIVE))
                .thenReturn(Optional.of(corrupt));

        assertThatThrownBy(() -> service.readSharedPlan("tok-existing"))
                .isInstanceOf(TripPublicationService.SharedPlanUnavailableException.class);

        verifyNoInteractions(plannerService, tripRepository);
    }

    // -------------------------------------------------------- explore listing

    @Test
    void aStrangerCannotListSomeoneElsesPlanOnExplore() {
        when(tripRepository.findByIdAndUserId(TRIP, STRANGER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateExploreListing(TRIP, STRANGER, true, null))
                .isInstanceOf(TripService.TripNotFoundException.class);

        verifyNoInteractions(publicationRepository);
    }

    @Test
    void publishingIsUnlistedUnlessTheOwnerOptsIn() {
        givenOwnedTripAtVersion(3L);
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.empty());
        when(tokenGenerator.generate()).thenReturn("tok-abc");
        when(publicationRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        PublicationResponse response = service.publish(TRIP, OWNER, publishRequest(3L, null));

        assertThat(response.listedInExplore()).isFalse();
    }

    @Test
    void publishingWithTheOptInListsThePlanAndStampsWhen() {
        givenOwnedTripAtVersion(3L);
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.empty());
        when(tokenGenerator.generate()).thenReturn("tok-abc");
        ArgumentCaptor<TripPublication> saved = ArgumentCaptor.forClass(TripPublication.class);
        when(publicationRepository.saveAndFlush(saved.capture())).thenAnswer(call -> call.getArgument(0));

        PublicationResponse response = service.publish(TRIP, OWNER,
                new PublishPlanRequest(3L, null, PublicationOptions.none(), true, null));

        assertThat(response.listedInExplore()).isTrue();
        assertThat(saved.getValue().getListedAt()).isNotNull();
    }

    @Test
    void publishingANameFreezesItForTheLinkAndExploreCard() {
        givenOwnedTripAtVersion(3L);
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.empty());
        when(tokenGenerator.generate()).thenReturn("tok-title");
        ArgumentCaptor<TripPublication> saved = ArgumentCaptor.forClass(TripPublication.class);
        when(publicationRepository.saveAndFlush(saved.capture())).thenAnswer(call -> call.getArgument(0));

        PublicationResponse response = service.publish(TRIP, OWNER,
                new PublishPlanRequest(3L, null, PublicationOptions.none(), true, null,
                        "  Top 10 things to do in Japan\u0007  "));

        assertThat(response.title()).isEqualTo("Top 10 things to do in Japan");
        when(publicationRepository.findByTokenAndStatus("tok-title", TripPublication.PublicationStatus.ACTIVE))
                .thenReturn(Optional.of(saved.getValue()));
        assertThat(service.readSharedPlan("tok-title").plan().title())
                .isEqualTo("Top 10 things to do in Japan");
    }

    /** Pressing Update must not bump a listed plan back to the top of Explore. */
    @Test
    void updatingAListedPlanKeepsItsOriginalListingTime() {
        givenOwnedTripAtVersion(3L);
        TripPublication listed = activePublication(2);
        Instant originallyListed = Instant.parse("2026-09-01T00:00:00Z");
        listed.setListedInExplore(true);
        listed.setListedAt(originallyListed);
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.of(listed));
        when(publicationRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        service.publish(TRIP, OWNER, new PublishPlanRequest(3L, 2, PublicationOptions.none(), true, null));

        assertThat(listed.getListedAt()).isEqualTo(originallyListed);
    }

    @Test
    void cannotListAPlanThatIsNotPublished() {
        givenOwnedTripAtVersion(3L);
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateExploreListing(TRIP, OWNER, true, null))
                .isInstanceOf(TripPublicationService.PublicationConflictException.class);

        verify(publicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void unlistingTakesEffectWithoutRepublishingTheContent() {
        givenOwnedTripAtVersion(3L);
        TripPublication listed = activePublication(2);
        listed.setListedInExplore(true);
        listed.setListedAt(Instant.EPOCH);
        String snapshotBefore = listed.getSnapshot();
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.of(listed));
        when(publicationRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        PublicationResponse response = service.updateExploreListing(TRIP, OWNER, false, null);

        assertThat(response.listedInExplore()).isFalse();
        assertThat(listed.getListedAt()).isNull();
        assertThat(listed.getSnapshot()).isEqualTo(snapshotBefore);
        assertThat(listed.getRevision()).isEqualTo(2);
        verifyNoInteractions(plannerService);
    }

    /** Stopping sharing must also remove the plan from public discovery. */
    @Test
    void stoppingSharingAlsoTakesThePlanOffExplore() {
        when(tripRepository.findByIdAndUserId(TRIP, OWNER)).thenReturn(Optional.of(trip(3L)));
        TripPublication listed = activePublication(2);
        listed.setListedInExplore(true);
        listed.setListedAt(Instant.EPOCH);
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.of(listed));
        when(publicationRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        service.revoke(TRIP, OWNER);

        assertThat(listed.getListedInExplore()).isFalse();
        assertThat(listed.getListedAt()).isNull();
    }

    @Test
    void theFeedAsksOnlyForSnapshotsTheCurrentSanitizerWouldServe() {
        when(publicationRepository.findListedInExplore(anyInt(), any())).thenReturn(Page.empty());

        service.listExplorePlans(PageRequest.of(0, 20));

        verify(publicationRepository)
                .findListedInExplore(PlanPublicationSanitizer.SANITIZER_VERSION, PageRequest.of(0, 20));
    }

    @Test
    void theFeedLeavesOutAnUnreadableSnapshotInsteadOfFailingForEveryone() {
        TripPublication good = activePublication(2);
        good.setListedInExplore(true);
        good.setListedAt(Instant.EPOCH);
        TripPublication corrupt = activePublication(2);
        corrupt.setSnapshot("{ not json");
        when(publicationRepository.findListedInExplore(anyInt(), any()))
                .thenReturn(new PageImpl<>(List.of(good, corrupt), PageRequest.of(0, 20), 2));

        Page<ExplorePlanSummary> page = service.listExplorePlans(PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().token()).isEqualTo("tok-existing");
        assertThat(page.getContent().getFirst().title()).isEqualTo("Songkran road trip");
    }

    @Test
    void aSharedPlanTellsTheExplorePageWhetherItIsStillListed() {
        TripPublication listed = activePublication(2);
        listed.setListedInExplore(true);
        listed.setListedAt(Instant.EPOCH);
        when(publicationRepository.findByTokenAndStatus("tok-existing", TripPublication.PublicationStatus.ACTIVE))
                .thenReturn(Optional.of(listed));

        assertThat(service.readSharedPlan("tok-existing").listedInExplore()).isTrue();
    }

    // ------------------------------------------------------------------ byline

    @Test
    void publishingFreezesTheOwnersBylineAsTheySawIt() {
        givenOwnedTripAtVersion(3L);
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.empty());
        when(tokenGenerator.generate()).thenReturn("tok-abc");
        when(publicationRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        PublicationResponse response = service.publish(TRIP, OWNER,
                new PublishPlanRequest(3L, null, PublicationOptions.none(), true, "  Kanya\u0007 S.  "));

        assertThat(response.authorDisplayName()).isEqualTo("Kanya S.");
    }

    @Test
    void listingWithoutANameKeepsTheBylineAlreadyPublished() {
        givenOwnedTripAtVersion(3L);
        TripPublication published = activePublication(2);
        published.setAuthorDisplayName("Kanya S.");
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.of(published));
        when(publicationRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        service.updateExploreListing(TRIP, OWNER, true, null);

        assertThat(published.getAuthorDisplayName()).isEqualTo("Kanya S.");
    }

    @Test
    void listingWithANameRefreshesTheByline() {
        givenOwnedTripAtVersion(3L);
        TripPublication published = activePublication(2);
        published.setAuthorDisplayName("Old name");
        when(publicationRepository.findByTripId(TRIP)).thenReturn(Optional.of(published));
        when(publicationRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        service.updateExploreListing(TRIP, OWNER, true, "Kanya S.");

        assertThat(published.getAuthorDisplayName()).isEqualTo("Kanya S.");
    }

    @Test
    void theFeedAndTheSharedPlanCarryTheBylineButNoAccountIdentifier() {
        TripPublication listed = activePublication(2);
        listed.setListedInExplore(true);
        listed.setListedAt(Instant.EPOCH);
        listed.setAuthorDisplayName("Kanya S.");
        when(publicationRepository.findListedInExplore(anyInt(), any()))
                .thenReturn(new PageImpl<>(List.of(listed), PageRequest.of(0, 20), 1));
        when(publicationRepository.findByTokenAndStatus("tok-existing", TripPublication.PublicationStatus.ACTIVE))
                .thenReturn(Optional.of(listed));

        ExplorePlanSummary card = service.listExplorePlans(PageRequest.of(0, 20)).getContent().getFirst();
        SharedPlanResponse shared = service.readSharedPlan("tok-existing");

        assertThat(card.authorName()).isEqualTo("Kanya S.");
        assertThat(shared.authorName()).isEqualTo("Kanya S.");
        assertThat(card.toString()).doesNotContain(OWNER.toString());
        assertThat(shared.toString()).doesNotContain(OWNER.toString());
    }

    // ------------------------------------------------------------------ fixtures

    private void givenOwnedTripAtVersion(long version) {
        when(tripRepository.findByIdAndUserId(TRIP, OWNER)).thenReturn(Optional.of(trip(version)));
    }

    private Trip trip(long version) {
        return Trip.builder()
                .id(TRIP)
                .userId(OWNER)
                .displayName("Songkran road trip")
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 3, 8))
                .destinationId("places/bangkok")
                .destinationName("Bangkok")
                .destinationCity("Bangkok")
                .destinationCountry("Thailand")
                .destinationCountryCode("TH")
                .visibility(TripVisibility.PRIVATE)
                .budgetCurrency(CurrencyCode.THB)
                .budgetAmount(BigDecimal.ZERO)
                .version(version)
                .createdAt(Instant.EPOCH)
                .updatedAt(Instant.EPOCH)
                .build();
    }

    private TripPublication activePublication(int revision) {
        return TripPublication.builder()
                .id(UUID.fromString("00000000-0000-4000-8000-0000000000f1"))
                .tripId(TRIP)
                .token("tok-existing")
                .snapshot("""
                        {"sanitizerVersion":1,"title":"Songkran road trip","dayCount":0,"days":[]}""")
                .sanitizerVersion(PlanPublicationSanitizer.SANITIZER_VERSION)
                .sourceTripVersion(3L)
                .includeDates(false)
                .includeNotes(false)
                .includeBudget(false)
                .status(TripPublication.PublicationStatus.ACTIVE)
                .revision(revision)
                .version(0L)
                .publishedAt(Instant.EPOCH)
                .updatedAt(Instant.EPOCH)
                .build();
    }

    private PublishPlanRequest publishRequest(Long expectedTripVersion, Integer expectedRevision) {
        return new PublishPlanRequest(expectedTripVersion, expectedRevision, PublicationOptions.none(), false, null);
    }

    private String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected the read to fail");
        } catch (TripPublicationService.SharedPlanUnavailableException expected) {
            return expected.getMessage();
        }
    }
}
