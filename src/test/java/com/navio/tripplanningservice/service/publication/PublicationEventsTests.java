package com.navio.tripplanningservice.service.publication;

import com.navio.tripplanningservice.model.OutboxEvent;
import com.navio.tripplanningservice.model.TripPublication;
import com.navio.tripplanningservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicationEventsTests {

    private static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-00000000000a");
    private static final UUID PUBLICATION = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    private static final UUID TRIP = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final Instant LISTED_AT = Instant.parse("2026-10-31T08:00:00Z");

    @Mock OutboxEventRepository outbox;

    private PublicationEvents events;

    @BeforeEach
    void setUp() {
        events = new PublicationEvents(outbox);
    }

    @Test
    void aPlanNewlyListedUnderItsOwnersNameIsAnnouncedWithIdentifiersOnly() {
        when(outbox.save(any())).thenAnswer(call -> call.getArgument(0));

        boolean staged = events.recordChange(PublicationEvents.Before.NONE, listed("tok-a", "Kanya S."), OWNER);

        assertThat(staged).isTrue();
        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("SharedPlanListed.v1");
        assertThat(event.getValue().getPartitionKey()).isEqualTo(PUBLICATION.toString());
        assertThat(event.getValue().getAggregateId()).isEqualTo(PUBLICATION);
        assertThat(event.getValue().getPayload())
                .containsEntry("ownerId", OWNER.toString())
                .containsEntry("token", "tok-a")
                .containsEntry("publicationId", PUBLICATION.toString())
                .containsEntry("listedAt", LISTED_AT.toString())
                .doesNotContainKeys("title", "snapshot", "authorDisplayName");
    }

    @Test
    void updatingAnAlreadyAnnouncedPlanStaysSilent() {
        TripPublication after = listed("tok-a", "Kanya S.");

        boolean staged = events.recordChange(new PublicationEvents.Before(true, "tok-a"), after, OWNER);

        assertThat(staged).isFalse();
        verifyNoInteractions(outbox);
    }

    @Test
    void aLinkOnlyShareIsNotAnnounced() {
        TripPublication after = listed("tok-a", "Kanya S.");
        after.applyListing(false, LISTED_AT);

        assertThat(events.recordChange(PublicationEvents.Before.NONE, after, OWNER)).isFalse();
        verifyNoInteractions(outbox);
    }

    @Test
    void anAnonymousListingIsNotAnnounced() {
        assertThat(events.recordChange(PublicationEvents.Before.NONE, listed("tok-a", null), OWNER)).isFalse();
        assertThat(events.recordChange(PublicationEvents.Before.NONE, listed("tok-a", "   "), OWNER)).isFalse();
        verifyNoInteractions(outbox);
    }

    @Test
    void addingABylineToAnAnonymousListingAnnouncesIt() {
        when(outbox.save(any())).thenAnswer(call -> call.getArgument(0));

        boolean staged = events.recordChange(new PublicationEvents.Before(false, "tok-a"), listed("tok-a", "Kanya S."), OWNER);

        assertThat(staged).isTrue();
    }

    @Test
    void republishingUnderANewLinkAfterStoppingIsANewAnnouncement() {
        when(outbox.save(any())).thenAnswer(call -> call.getArgument(0));

        boolean staged = events.recordChange(new PublicationEvents.Before(true, "tok-old"), listed("tok-new", "Kanya S."), OWNER);

        assertThat(staged).isTrue();
    }

    @Test
    void aRevokedPublicationIsNeverAnnounced() {
        TripPublication revoked = listed("tok-a", "Kanya S.");
        revoked.setStatus(TripPublication.PublicationStatus.REVOKED);

        assertThat(events.recordChange(PublicationEvents.Before.NONE, revoked, OWNER)).isFalse();
        verifyNoInteractions(outbox);
    }

    @Test
    void theBeforeStateOfAMissingPublicationIsNotAnnounceable() {
        assertThat(PublicationEvents.Before.of(null)).isEqualTo(PublicationEvents.Before.NONE);
        assertThat(PublicationEvents.Before.of(listed("tok-a", "Kanya S.")))
                .isEqualTo(new PublicationEvents.Before(true, "tok-a"));
    }

    private static TripPublication listed(String token, String byline) {
        TripPublication publication = TripPublication.builder()
                .id(PUBLICATION)
                .tripId(TRIP)
                .token(token)
                .status(TripPublication.PublicationStatus.ACTIVE)
                .revision(1)
                .authorDisplayName(byline)
                .build();
        publication.applyListing(true, LISTED_AT);
        return publication;
    }
}
