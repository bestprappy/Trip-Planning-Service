package com.navio.tripplanningservice.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.navio.tripplanningservice.dto.PlannerAnchorDto;
import com.navio.tripplanningservice.dto.PlannerBlockDto;
import com.navio.tripplanningservice.dto.PlannerBudgetDto;
import com.navio.tripplanningservice.dto.PlannerChecklistSubItemDto;
import com.navio.tripplanningservice.dto.PlannerDestinationDto;
import com.navio.tripplanningservice.dto.PlannerEvChargerDto;
import com.navio.tripplanningservice.dto.PlannerExpenseDto;
import com.navio.tripplanningservice.dto.PlannerItemDto;
import com.navio.tripplanningservice.dto.PlannerSnapshotResponse;
import com.navio.tripplanningservice.dto.publication.PublicAnchorDto;
import com.navio.tripplanningservice.dto.publication.PublicDayDto;
import com.navio.tripplanningservice.dto.publication.PublicItemDto;
import com.navio.tripplanningservice.dto.publication.PublicPlanSnapshot;
import com.navio.tripplanningservice.dto.publication.PublicationOptions;
import com.navio.tripplanningservice.model.CurrencyCode;
import com.navio.tripplanningservice.model.Trip;
import com.navio.tripplanningservice.model.TripVisibility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adversarial fixtures for the published-plan privacy boundary.
 *
 * <p>These are not happy-path tests. Each one plants something that must not
 * reach a stranger and asserts it did not. The anchoring case is
 * {@link #noPrivateStringSurvivesAnywhereInTheSerialisedSnapshot()}, which
 * writes the owner's home address into every field that could carry it and then
 * greps the JSON a recipient would actually be served.
 */
class PlanPublicationSanitizerTests {

    private static final String HOME = "88/12 Soi Ari 4, Phaya Thai, Bangkok 10400";
    private static final String HOME_PLACE_ID = "saved-place-home-7f3a";
    private static final double HOME_LAT = 13.7796;
    private static final double HOME_LNG = 100.5414;

    private final PlanPublicationSanitizer sanitizer = new PlanPublicationSanitizer();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // ---------------------------------------------------------------- anchors

    @Test
    void replacesASavedPlaceDayAnchorWithAPlaceholder() {
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1))
                .withStart(new PlannerAnchorDto(HOME_PLACE_ID, "SAVED_PLACE", "Home", HOME, HOME_LAT, HOME_LNG))
                .build();

        PublicDayDto published = sanitizeDays(day, PublicationOptions.none()).getFirst();

        assertThat(published.startsAt()).isEqualTo(PublicAnchorDto.redactedStart());
        assertThat(published.startsAt().name()).isEqualTo("Private start location");
        assertThat(published.startsAt().redacted()).isTrue();
    }

    @Test
    void usesTheEndWordingWhenThePrivateAnchorEndsTheDay() {
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1))
                .withEnd(new PlannerAnchorDto(HOME_PLACE_ID, "SAVED_PLACE", "Home", HOME, HOME_LAT, HOME_LNG))
                .build();

        PublicDayDto published = sanitizeDays(day, PublicationOptions.none()).getFirst();

        assertThat(published.endsAt().name()).isEqualTo("Private end location");
        assertThat(published.startsAt()).isNull();
    }

    @Test
    void publishesAHotelAnchorBecauseItIsItineraryContent() {
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1))
                .withEnd(new PlannerAnchorDto("places/abc", "PLACE", "Bangkok Marriott", "Sukhumvit", 13.7, 100.5))
                .build();

        PublicAnchorDto published = sanitizeDays(day, PublicationOptions.none()).getFirst().endsAt();

        assertThat(published.name()).isEqualTo("Bangkok Marriott");
        assertThat(published.redacted()).isFalse();
    }

    /**
     * The allowlist, stated as a test.
     *
     * <p>{@code PlannerService} warns that an unrecognised anchor kind "would be
     * published by default — the unsafe direction". These are the kinds that
     * would slip through a {@code !"SAVED_PLACE".equals(kind)} check: a new kind
     * added later, a lowercased one, a corrupted one, an empty one.
     */
    @ParameterizedTest
    @ValueSource(strings = {"HOME", "USER_SAVED_PLACE", "saved-place", "CONTACT", "", "   ", "null"})
    void redactsAnyAnchorKindItDoesNotExplicitlyAllow(String unknownKind) {
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1))
                .withStart(new PlannerAnchorDto(HOME_PLACE_ID, unknownKind, HOME, HOME, HOME_LAT, HOME_LNG))
                .build();

        PublicAnchorDto published = sanitizeDays(day, PublicationOptions.none()).getFirst().startsAt();

        assertThat(published.redacted()).isTrue();
        assertThat(published.name()).isEqualTo("Private start location");
    }

    @Test
    void redactsASavedPlaceAnchorWhateverTheCasingOfItsKind() {
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1))
                .withStart(new PlannerAnchorDto(HOME_PLACE_ID, "saved_place", "Home", HOME, HOME_LAT, HOME_LNG))
                .build();

        assertThat(sanitizeDays(day, PublicationOptions.none()).getFirst().startsAt().redacted()).isTrue();
    }

    /**
     * A day with no anchor of its own inherits the previous day's end in the
     * planner. That inheritance must not be materialised here: if day 2 has no
     * start, publishing day 1's redacted end as day 2's start would restate the
     * private location under a different key.
     */
    @Test
    void aDayWithoutItsOwnAnchorDoesNotInheritTheNeighbouringPrivateOne() {
        PlannerBlockDto dayOne = itineraryDay("d1", LocalDate.of(2026, 3, 1))
                .withEnd(new PlannerAnchorDto(HOME_PLACE_ID, "SAVED_PLACE", "Home", HOME, HOME_LAT, HOME_LNG))
                .build();
        PlannerBlockDto dayTwo = itineraryDay("d2", LocalDate.of(2026, 3, 2)).build();

        List<PublicDayDto> days = sanitizeDays(PublicationOptions.none(), dayOne, dayTwo);

        assertThat(days.get(1).startsAt()).isNull();
        assertThat(days.get(1).endsAt()).isNull();
    }

    // ------------------------------------------------------------ derived data

    /**
     * The reconstruction case the whole design turns on.
     *
     * <p>A charging stop carries a charge duration that the optimiser derived
     * from the day's real start — the redacted one. Publishing it would state
     * how far the private location is from a named station. The public charger
     * type has no field for it, which is the point: this asserts the type stayed
     * that way.
     */
    @Test
    void publishesTheStationButNotTheChargePlanDerivedFromTheRemovedAnchor() {
        PlannerEvChargerDto charger = new PlannerEvChargerDto(
                List.of("CCS2", "TYPE2"), 150.0, 4, 2, "8 THB/kWh", "24 hours",
                38, "EA Anywhere", "AUTO", true, 82);
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1))
                .withStart(new PlannerAnchorDto(HOME_PLACE_ID, "SAVED_PLACE", "Home", HOME, HOME_LAT, HOME_LNG))
                .withItems(place("i1", "PTT Station Rama II").withCharger(charger).build())
                .build();

        PublicItemDto published = sanitizeDays(day, PublicationOptions.none()).getFirst().items().getFirst();

        assertThat(published.type()).isEqualTo("charger");
        assertThat(published.charger().operatorName()).isEqualTo("EA Anywhere");
        assertThat(published.charger().maxKw()).isEqualTo(150.0);
        assertThat(published.charger().connectorTypes()).containsExactly("CCS2", "TYPE2");

        String json = toJson(sanitizeSnapshot(PublicationOptions.none(), day));
        assertThat(json)
                .as("charge minutes and target battery are derived from the redacted start")
                .doesNotContain("38")
                .doesNotContain("82")
                .doesNotContain("targetBatteryPct")
                .doesNotContain("estimatedChargeMinutes")
                .doesNotContain("selectionSource");
    }

    @Test
    void neverPublishesCoordinatesAddressesOrProviderIdsForAnyPlace() {
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1))
                .withItems(place("i1", "Wat Arun").build())
                .build();

        String json = toJson(sanitizeSnapshot(PublicationOptions.none(), day));

        assertThat(json)
                .doesNotContain("\"lat\"")
                .doesNotContain("\"lng\"")
                .doesNotContain("\"address\"")
                .doesNotContain("\"placeId\"")
                .doesNotContain("13.74")
                .doesNotContain("100.49");
    }

    // ----------------------------------------------------------------- options

    @Test
    void omitsPerStopCostUntilTheOwnerOptsIntoBudget() {
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1))
                .withItems(place("i1", "Jay Fai").withCost(1450.0).build())
                .build();

        assertThat(sanitizeDays(day, PublicationOptions.none()).getFirst().items().getFirst().cost()).isNull();
        assertThat(toJson(sanitizeSnapshot(PublicationOptions.none(), day))).doesNotContain("1450");

        PublicationOptions withBudget = new PublicationOptions(false, false, true);
        assertThat(sanitizeDays(day, withBudget).getFirst().items().getFirst().cost()).isEqualTo(1450.0);
    }

    @Test
    void dropsFreeTextNoteItemsEntirelyUntilTheOwnerOptsIntoNotes() {
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1))
                .withItems(note("n1", "Dad's spare key is under the blue pot at " + HOME))
                .build();

        assertThat(sanitizeDays(day, PublicationOptions.none()).getFirst().items()).isEmpty();
        assertThat(toJson(sanitizeSnapshot(PublicationOptions.none(), day))).doesNotContain("spare key");

        PublicationOptions withNotes = new PublicationOptions(false, true, false);
        PublicItemDto published = sanitizeDays(day, withNotes).getFirst().items().getFirst();
        assertThat(published.type()).isEqualTo("note");
        assertThat(published.noteContent()).contains("spare key");
    }

    @Test
    void dropsAPlaceNoteWhileKeepingThePlaceItself() {
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1))
                .withItems(place("i1", "Wat Arun").withNotes("Ask for Khun Nok, she has our booking").build())
                .build();

        PublicItemDto published = sanitizeDays(day, PublicationOptions.none()).getFirst().items().getFirst();

        assertThat(published.name()).isEqualTo("Wat Arun");
        assertThat(published.notes()).isNull();
    }

    @Test
    void publishesChecklistLabelsWithoutWhichBoxesAreTicked() {
        PlannerItemDto checklist = new PlannerItemDto(
                "c1", "checklist", null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, "Packing",
                List.of(new PlannerChecklistSubItemDto("s1", "Passport", true),
                        new PlannerChecklistSubItemDto("s2", "Charger cable", false)));
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1)).withItems(checklist).build();

        PublicationOptions withNotes = new PublicationOptions(false, true, false);
        PublicItemDto published = sanitizeDays(day, withNotes).getFirst().items().getFirst();

        assertThat(published.checklistTitle()).isEqualTo("Packing");
        assertThat(published.checklistLabels()).containsExactly("Passport", "Charger cable");
        assertThat(toJson(sanitizeSnapshot(withNotes, day))).doesNotContain("checked");
    }

    @Test
    void removesDatesFromEveryFieldThatEncodesOneNotJustTheDayDate() {
        PlannerBudgetDto budget = new PlannerBudgetDto(CurrencyCode.THB, new BigDecimal("20000.00"),
                List.of(new PlannerExpenseDto("e1", new BigDecimal("3200.00"), "Hotel", "accommodation",
                        LocalDate.of(2026, 3, 2))));
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1)).build();

        PublicPlanSnapshot published = sanitizer.sanitize(
                trip(), new PlannerSnapshotResponse(List.of(day), budget, 7L, Instant.EPOCH),
                new PublicationOptions(false, false, true));

        assertThat(published.startDate()).isNull();
        assertThat(published.endDate()).isNull();
        assertThat(published.days().getFirst().date()).isNull();
        assertThat(published.days().getFirst().label()).isEqualTo("Day 1");
        assertThat(toJson(published))
                .as("an expense date is a travel date under another name")
                .doesNotContain("2026-03-01")
                .doesNotContain("2026-03-02")
                .doesNotContain("2026-03-08");
    }

    @Test
    void labelsDaysSequentiallySoTheItineraryReadsWithoutDates() {
        List<PublicDayDto> days = sanitizeDays(PublicationOptions.none(),
                itineraryDay("d1", LocalDate.of(2026, 3, 1)).build(),
                itineraryDay("d2", LocalDate.of(2026, 3, 2)).build(),
                itineraryDay("d3", LocalDate.of(2026, 3, 3)).build());

        assertThat(days).extracting(PublicDayDto::label).containsExactly("Day 1", "Day 2", "Day 3");
    }

    @Test
    void treatsMissingOptionsAsPublishingTheLeastNotTheMost() {
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1))
                .withItems(place("i1", "Jay Fai").withCost(1450.0).withNotes("private").build())
                .build();

        PublicPlanSnapshot published = sanitizer.sanitize(
                trip(), new PlannerSnapshotResponse(List.of(day), 1L, Instant.EPOCH), null);

        assertThat(published.included()).isEqualTo(PublicationOptions.none());
        assertThat(published.startDate()).isNull();
        assertThat(published.budget()).isNull();
        assertThat(published.days().getFirst().items().getFirst().cost()).isNull();
    }

    // -------------------------------------------------------------- structural

    @Test
    void publishesItineraryDaysOnlyAndNotSideLists() {
        PlannerBlockDto list = new PlannerBlockDto(
                "l1", "list", "Packing ideas", LocalDate.of(2026, 3, 1), "slate",
                List.of(place("i1", "Buy adapter").build()));
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1)).build();

        PublicPlanSnapshot published = sanitizeSnapshot(PublicationOptions.none(), list, day);

        assertThat(published.days()).hasSize(1);
        assertThat(published.dayCount()).isEqualTo(1);
        assertThat(published.days().getFirst().label()).isEqualTo("Day 1");
    }

    @Test
    void carriesNoOwnerOrTripIdentifier() {
        UUID owner = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
        UUID tripId = UUID.fromString("00000000-0000-4000-8000-0000000000bb");
        Trip trip = trip();
        trip.setUserId(owner);
        trip.setId(tripId);

        String json = toJson(sanitizer.sanitize(trip,
                new PlannerSnapshotResponse(List.of(itineraryDay("d1", LocalDate.of(2026, 3, 1)).build()),
                        1L, Instant.EPOCH),
                PublicationOptions.none()));

        assertThat(json)
                .doesNotContain(owner.toString())
                .doesNotContain(tripId.toString())
                .doesNotContain("userId")
                .doesNotContain("tripId")
                .doesNotContain("version\":1");
    }

    @Test
    void stampsTheSanitiserVersionSoStaleSnapshotsCanBeRefusedOnRead() {
        PublicPlanSnapshot published = sanitizeSnapshot(
                PublicationOptions.none(), itineraryDay("d1", LocalDate.of(2026, 3, 1)).build());

        assertThat(published.sanitizerVersion()).isEqualTo(PlanPublicationSanitizer.SANITIZER_VERSION);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=",
            "/internal/private-media/owner-7f3a.jpg",
            "not a url at all"
    })
    void refusesAnImageUrlThatIsNotAnAbsoluteHttpOne(String hostileUrl) {
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1))
                .withItems(place("i1", "Wat Arun").withImage(hostileUrl).build())
                .build();

        assertThat(sanitizeDays(day, PublicationOptions.none()).getFirst().items().getFirst().imageUrl()).isNull();
    }

    @Test
    void keepsAnOrdinaryHttpsImage() {
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1))
                .withItems(place("i1", "Wat Arun").withImage("https://media.navio.app/p/abc.jpg").build())
                .build();

        assertThat(sanitizeDays(day, PublicationOptions.none()).getFirst().items().getFirst().imageUrl())
                .isEqualTo("https://media.navio.app/p/abc.jpg");
    }

    /**
     * The whole structural boundary in one assertion.
     *
     * <p>The owner's home is planted in every field the <em>system</em> holds it
     * in — both day anchors, the day destination, a place address, a provider
     * place id, raw coordinates — and every option is turned on, so no option
     * flag is doing the work. None of it may reach the recipient. If a future
     * edit adds a field that forwards a stored location, this fails.
     *
     * <p>Note what is deliberately not planted here: text the owner typed. That
     * case is {@link #ownerTypedTextIsPublishedWhenTheOwnerOptsIn()}, and the
     * difference between the two is the security model.
     */
    @Test
    void noStoredLocationSurvivesEvenWithEveryOptionTurnedOn() {
        PlannerEvChargerDto charger = new PlannerEvChargerDto(
                List.of("CCS2"), 150.0, 4, 2, "8 THB/kWh", "24 hours", 38, "EA", "AUTO", true, 82);
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1))
                .withStart(new PlannerAnchorDto(HOME_PLACE_ID, "SAVED_PLACE", HOME, HOME, HOME_LAT, HOME_LNG))
                .withEnd(new PlannerAnchorDto(HOME_PLACE_ID, "SAVED_PLACE", HOME, HOME, HOME_LAT, HOME_LNG))
                .withDestination(new PlannerDestinationDto(HOME_PLACE_ID, HOME, HOME_LAT, HOME_LNG, "Thailand"))
                .withItems(place("i1", "PTT Station").withAddress(HOME).withCharger(charger).build())
                .build();
        PlannerBudgetDto budget = new PlannerBudgetDto(CurrencyCode.THB, new BigDecimal("20000.00"),
                List.of(new PlannerExpenseDto("e1", new BigDecimal("3200.00"), "Hotel", "accommodation",
                        LocalDate.of(2026, 3, 2))));

        PublicPlanSnapshot published = sanitizer.sanitize(
                trip(),
                new PlannerSnapshotResponse(List.of(day), budget, 7L, Instant.EPOCH),
                new PublicationOptions(true, true, true));

        assertThat(toJson(published))
                .doesNotContain(HOME)
                .doesNotContain("Soi Ari")
                .doesNotContain(HOME_PLACE_ID)
                .doesNotContain("13.7796")
                .doesNotContain("100.5414");
    }

    /**
     * The limit of what a sanitiser can do, stated as a test rather than left
     * as an assumption.
     *
     * <p>If the owner types their own address into a note or an expense label,
     * opting into notes and budget publishes it. No allowlist can distinguish
     * "my address" from "the hotel's address" in free text the owner wrote. This
     * is why the dialog has a preview of the real sanitised output and a warning
     * beside the notes option, and why both options are off by default — the
     * defence is informed consent, not filtering.
     *
     * <p>If this test ever fails, someone has added silent content filtering.
     * Reconsider it: quietly dropping part of a note the owner chose to publish
     * is its own failure, because they will believe it was shared.
     */
    @Test
    void ownerTypedTextIsPublishedWhenTheOwnerOptsIn() {
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1))
                .withItems(note("n1", "Spare key is at " + HOME))
                .build();
        PlannerBudgetDto budget = new PlannerBudgetDto(CurrencyCode.THB, new BigDecimal("20000.00"),
                List.of(new PlannerExpenseDto("e1", new BigDecimal("3200.00"), "Taxi to " + HOME,
                        "transport", null)));

        PublicPlanSnapshot published = sanitizer.sanitize(
                trip(),
                new PlannerSnapshotResponse(List.of(day), budget, 7L, Instant.EPOCH),
                new PublicationOptions(false, true, true));

        assertThat(published.days().getFirst().items().getFirst().noteContent()).contains(HOME);
        assertThat(published.budget().expenses().getFirst().label()).contains(HOME);
    }

    /** The same owner-typed text, with the options left alone, does not travel. */
    @Test
    void ownerTypedTextStaysPrivateUnderTheDefaultOptions() {
        PlannerBlockDto day = itineraryDay("d1", LocalDate.of(2026, 3, 1))
                .withItems(note("n1", "Spare key is at " + HOME))
                .build();
        PlannerBudgetDto budget = new PlannerBudgetDto(CurrencyCode.THB, new BigDecimal("20000.00"),
                List.of(new PlannerExpenseDto("e1", new BigDecimal("3200.00"), "Taxi to " + HOME,
                        "transport", null)));

        PublicPlanSnapshot published = sanitizer.sanitize(
                trip(),
                new PlannerSnapshotResponse(List.of(day), budget, 7L, Instant.EPOCH),
                PublicationOptions.none());

        assertThat(toJson(published)).doesNotContain(HOME).doesNotContain("Soi Ari");
    }

    // ------------------------------------------------------------------ fixtures

    private Trip trip() {
        return Trip.builder()
                .id(UUID.fromString("00000000-0000-4000-8000-0000000000bb"))
                .userId(UUID.fromString("00000000-0000-4000-8000-0000000000aa"))
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
                .budgetAmount(new BigDecimal("20000.00"))
                .version(7L)
                .createdAt(Instant.EPOCH)
                .updatedAt(Instant.EPOCH)
                .build();
    }

    private PublicPlanSnapshot sanitizeSnapshot(PublicationOptions options, PlannerBlockDto... blocks) {
        return sanitizer.sanitize(
                trip(), new PlannerSnapshotResponse(List.of(blocks), 7L, Instant.EPOCH), options);
    }

    private List<PublicDayDto> sanitizeDays(PublicationOptions options, PlannerBlockDto... blocks) {
        return sanitizeSnapshot(options, blocks).days();
    }

    private List<PublicDayDto> sanitizeDays(PlannerBlockDto block, PublicationOptions options) {
        return sanitizeDays(options, block);
    }

    private String toJson(PublicPlanSnapshot snapshot) {
        try {
            return mapper.writeValueAsString(snapshot);
        } catch (Exception exception) {
            throw new IllegalStateException("snapshot is not serialisable", exception);
        }
    }

    private static DayBuilder itineraryDay(String id, LocalDate date) {
        return new DayBuilder(id, date);
    }

    private static PlannerItemDto note(String id, String content) {
        return new PlannerItemDto(id, "note", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, content, null, null);
    }

    private static PlaceBuilder place(String id, String name) {
        return new PlaceBuilder(id, name);
    }

    private static final class DayBuilder {
        private final String id;
        private final LocalDate date;
        private PlannerAnchorDto start;
        private PlannerAnchorDto end;
        private PlannerDestinationDto destination;
        private List<PlannerItemDto> items = List.of();

        private DayBuilder(String id, LocalDate date) {
            this.id = id;
            this.date = date;
        }

        DayBuilder withStart(PlannerAnchorDto anchor) {
            this.start = anchor;
            return this;
        }

        DayBuilder withEnd(PlannerAnchorDto anchor) {
            this.end = anchor;
            return this;
        }

        DayBuilder withDestination(PlannerDestinationDto value) {
            this.destination = value;
            return this;
        }

        DayBuilder withItems(PlannerItemDto... values) {
            this.items = List.of(values);
            return this;
        }

        PlannerBlockDto build() {
            return new PlannerBlockDto(id, "itinerary", "Bangkok", date, "slate",
                    items, destination, start, end);
        }
    }

    private static final class PlaceBuilder {
        private final String id;
        private final String name;
        private String address = "Wat Arun Rd, Bangkok";
        private String notes;
        private String imageUrl;
        private Double cost;
        private PlannerEvChargerDto charger;

        private PlaceBuilder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        PlaceBuilder withAddress(String value) {
            this.address = value;
            return this;
        }

        PlaceBuilder withNotes(String value) {
            this.notes = value;
            return this;
        }

        PlaceBuilder withImage(String value) {
            this.imageUrl = value;
            return this;
        }

        PlaceBuilder withCost(Double value) {
            this.cost = value;
            return this;
        }

        PlaceBuilder withCharger(PlannerEvChargerDto value) {
            this.charger = value;
            return this;
        }

        PlannerItemDto build() {
            return new PlannerItemDto(id, "place", HOME_PLACE_ID, name, "A landmark", address,
                    13.7437, 100.4888, 4.6, 12000, imageUrl, notes, true,
                    "09:00", "11:00", cost, charger, null, null, null);
        }
    }
}
