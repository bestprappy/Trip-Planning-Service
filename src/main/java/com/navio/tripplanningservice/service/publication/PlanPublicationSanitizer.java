package com.navio.tripplanningservice.service.publication;

import com.navio.tripplanningservice.dto.PlannerAnchorDto;
import com.navio.tripplanningservice.dto.PlannerBlockDto;
import com.navio.tripplanningservice.dto.PlannerChecklistSubItemDto;
import com.navio.tripplanningservice.dto.PlannerEvChargerDto;
import com.navio.tripplanningservice.dto.PlannerExpenseDto;
import com.navio.tripplanningservice.dto.PlannerItemDto;
import com.navio.tripplanningservice.dto.PlannerSnapshotResponse;
import com.navio.tripplanningservice.dto.publication.PublicAnchorDto;
import com.navio.tripplanningservice.dto.publication.PublicBudgetDto;
import com.navio.tripplanningservice.dto.publication.PublicChargerDto;
import com.navio.tripplanningservice.dto.publication.PublicDayDto;
import com.navio.tripplanningservice.dto.publication.PublicItemDto;
import com.navio.tripplanningservice.dto.publication.PublicPlanSnapshot;
import com.navio.tripplanningservice.dto.publication.PublicationOptions;
import com.navio.tripplanningservice.model.CurrencyCode;
import com.navio.tripplanningservice.model.Trip;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Turns an owner's private planner snapshot into the object a stranger may read.
 *
 * <p>This class is the entire privacy boundary for published plans. The read
 * route is anonymous, so nothing downstream re-checks what it emits: if a field
 * survives this method it is public. It is therefore written to fail closed —
 * an input it does not recognise is dropped or redacted, never passed through.
 *
 * <p>Two rules carry most of the weight:
 *
 * <ol>
 *   <li><b>Personal anchors never travel.</b> A {@code SAVED_PLACE} day anchor
 *       comes from the owner's address book and is frequently their home. It is
 *       replaced by a placeholder. Crucially the check is an <em>allowlist</em>
 *       of kinds that may be published, not a denylist of {@code SAVED_PLACE};
 *       a kind added later, or a corrupted one, redacts rather than leaks.</li>
 *   <li><b>Only stops keep coordinates.</b> Itinerary places and chargers are
 *       public provider places and publish their position. Day anchors, route
 *       geometry, leg distance, drive duration and battery projection do not,
 *       so no published figure can triangulate a redacted anchor.</li>
 * </ol>
 *
 * <p>The class is a pure function of its arguments — no repositories, no clock,
 * no request context — so every rule above is unit-testable without a database,
 * and {@code PlanPublicationSanitizerTests} exercises them against adversarial
 * fixtures rather than happy paths.
 */
@Component
public class PlanPublicationSanitizer {

    /**
     * Bumped whenever a rule here changes what may be published.
     *
     * <p>Published snapshots are frozen at publish time, so a redaction fix does
     * not reach rows written before it. The read path compares this constant
     * with the stored version and refuses anything older, which converts a
     * silently stale snapshot into a dead link the owner can re-publish.
     */
    public static final int SANITIZER_VERSION = 1;

    /** Same format as user-management's palette ids. */
    private static final java.util.regex.Pattern PALETTE_COLOR_ID = java.util.regex.Pattern.compile("[a-z][a-z0-9-]{0,39}");

    /**
     * Anchor kinds allowed into a published snapshot.
     *
     * <p>An allowlist, deliberately. {@code SAVED_PLACE} is absent because it is
     * personal; anything else is absent because it is unknown, and an unknown
     * kind is exactly the case where guessing wrong is unrecoverable.
     */
    private static final Set<String> PUBLISHABLE_ANCHOR_KINDS = Set.of("PLACE", "MANUAL");

    /** Only these schemes may be handed to a recipient's browser as an image. */
    private static final Set<String> ALLOWED_MEDIA_SCHEMES = Set.of("http", "https");

    public PublicPlanSnapshot sanitize(
            Trip trip,
            PlannerSnapshotResponse snapshot,
            PublicationOptions requestedOptions) {
        Objects.requireNonNull(trip, "trip");
        PublicationOptions options = PublicationOptions.orNone(requestedOptions);
        List<PlannerBlockDto> blocks = nullSafe(snapshot == null ? null : snapshot.blocks());

        List<PublicDayDto> days = new ArrayList<>();
        java.util.Map<String,String> publicRefs = new java.util.HashMap<>();
        int dayNumber = 0;
        for (PlannerBlockDto block : blocks) {
            if (block == null || !isItineraryDay(block)) {
                // A "list" block is a side list (packing, ideas), not a day of
                // travel. v1 publishes the itinerary only.
                continue;
            }
            dayNumber++;
            var publishedDay=sanitizeDay(block, dayNumber, options);
            days.add(publishedDay);
            collectRefs(block,publishedDay,options,publicRefs);
        }

        var selectedLists = new ArrayList<PublicDayDto>();
        com.navio.tripplanningservice.dto.publication.PublicStartingPartDto starting = null;
        for (var block : blocks) {
            if (block == null || !"list".equalsIgnoreCase(block.kind())) continue;
            if (block.id() != null && block.id().startsWith("starting-part-")) {
                if (options.includeStartingPart()) {
                    String content = nullSafe(block.items()).stream().filter(i -> i != null && "note".equals(i.type()))
                        .map(i -> safeText(i.content())).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining("\n\n"));
                    if (!content.isBlank()) starting = new com.navio.tripplanningservice.dto.publication.PublicStartingPartDto(safeText(block.title()),content);
                }
            } else if (options.listIds().contains(block.id())) {
                var section = sanitizeDay(block,-selectedLists.size()-1,options);
                collectRefs(block,section,options,publicRefs);
                selectedLists.add(new PublicDayDto("My List",null,safeText(block.title()),section.colorId(),null,null,section.items()));
            }
        }
        return new PublicPlanSnapshot(
                SANITIZER_VERSION,
                resolveTitle(trip),
                safeText(trip.getDestinationCity()),
                safeText(trip.getDestinationCountry()),
                options.includeDates() ? trip.getStartDate() : null,
                options.includeDates() ? trip.getEndDate() : null,
                days.size(),
                List.copyOf(days),
                options.includeBudget()
                        ? sanitizeBudget(snapshot == null ? null : snapshot.budget(),publicRefs)
                        : null,
                options.publicSummary(), starting, selectedLists.isEmpty()?null:List.copyOf(selectedLists), PublishedGarage.publish(snapshot == null ? null : snapshot.energyState(),options));
    }

    /** A publish-time name replaces only the public copy, never the private trip. */
    public PublicPlanSnapshot withPublicTitle(PublicPlanSnapshot snapshot, String requestedTitle) {
        String title = safeText(requestedTitle);
        if (title == null || title.isBlank()) return snapshot;
        return new PublicPlanSnapshot(snapshot.sanitizerVersion(), title,
                snapshot.destinationCity(), snapshot.destinationCountry(), snapshot.startDate(),
                snapshot.endDate(), snapshot.dayCount(), snapshot.days(), snapshot.budget(), snapshot.included(), snapshot.startingPart(), snapshot.lists(), snapshot.garage());
    }

    /**
     * Passes only palette ids. Older trips may hold free-form values (the
     * column once took hex codes), and readers embed the id in a CSS custom
     * property name, so anything else is dropped rather than published.
     */
    static String safeColorId(String colorId) {
        return colorId != null && PALETTE_COLOR_ID.matcher(colorId).matches() ? colorId : null;
    }

    private PublicDayDto sanitizeDay(PlannerBlockDto block, int dayNumber, PublicationOptions options) {
        List<PublicItemDto> items = new ArrayList<>();
        PlannerItemDto previousStop = null;
        PublicItemDto previousPublic = null;
        for (PlannerItemDto item : nullSafe(block.items())) {
            PublicItemDto sanitized = sanitizeItem(item, options);
            if (sanitized != null && ("place".equals(sanitized.type()) || "charger".equals(sanitized.type()))) {
                boolean publicLeg = previousPublic != null && previousPublic.lat() != null && previousPublic.lng() != null
                        && sanitized.lat() != null && sanitized.lng() != null;
                String mode = publicLeg ? PublishedTransport.resolve(previousStop, item) : null;
                String key = publicLeg && PublishedTransport.transit(mode) && item.arrivalRouteKey() != null && item.arrivalRouteKey().isTextual()
                        ? PublishedTransport.routeKey(item.arrivalRouteKey().asText()) : null;
                String placeType = sanitized.lat() != null ? PublishedTransport.type(item) : null;
                sanitized = sanitized.withTransport(mode,key,placeType == null || placeType.isEmpty() ? null : placeType);
                previousStop = item;
                previousPublic = sanitized;
            } else if (item == null || (!"note".equalsIgnoreCase(item.type()) && !"checklist".equalsIgnoreCase(item.type()))) {
                previousStop = null;
                previousPublic = null;
            }
            if (sanitized != null) {
                var moments = item.schedule() == null ? null : java.util.stream.IntStream.range(0,item.schedule().size()).mapToObj(index -> {
                    var moment=item.schedule().get(index);
                    return new com.navio.tripplanningservice.dto.PlannerScheduleEntryDto("moment-"+index,moment.time(),moment.timeEnd(),Objects.requireNonNullElse(safeText(moment.label()),""));
                }).toList();
                items.add(sanitized.withDetails("public-"+dayNumber+"-"+items.size(),moments,Set.of("fixed","calculated").contains(Objects.requireNonNullElse(item.timeSource(),""))?item.timeSource():null));
            }
        }

        return new PublicDayDto(
                "Day " + dayNumber,
                options.includeDates() ? block.date() : null,
                safeText(block.title()),
                safeColorId(block.colorId()),
                sanitizeAnchor(block.startAnchor(), true),
                sanitizeAnchor(block.endAnchor(), false),
                List.copyOf(items));
    }

    /**
     * @param isStart chooses the placeholder wording; a redacted start must not
     *                read as an end, or the reader mis-orders the day
     */
    private PublicAnchorDto sanitizeAnchor(PlannerAnchorDto anchor, boolean isStart) {
        if (anchor == null) {
            // The day inherits its start from the previous day's end. Stays
            // absent: inventing an anchor here is how a neighbouring day's
            // private location would propagate into a day that never had one.
            return null;
        }
        String kind = anchor.kind() == null ? "" : anchor.kind().trim().toUpperCase(Locale.ROOT);
        if (!PUBLISHABLE_ANCHOR_KINDS.contains(kind)) {
            return isStart ? PublicAnchorDto.redactedStart() : PublicAnchorDto.redactedEnd();
        }
        String name = safeText(anchor.name());
        if (name == null || name.isBlank()) {
            return isStart ? PublicAnchorDto.redactedStart() : PublicAnchorDto.redactedEnd();
        }
        return PublicAnchorDto.named(name);
    }

    /** @return null when the item must not appear at all under these options */
    private PublicItemDto sanitizeItem(PlannerItemDto item, PublicationOptions options) {
        if (item == null || item.type() == null) {
            return null;
        }
        String type = item.type().trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "place" -> sanitizePlace(item, options);
            // A note's whole substance is its text, and a checklist's is its
            // labels. Neither can be included "without notes", so both are
            // dropped entirely rather than published as empty shells.
            case "note" -> options.includeNotes() ? sanitizeNote(item) : null;
            case "checklist" -> options.includeNotes() ? sanitizeChecklist(item) : null;
            // A section is structure, like the day itself: it travels with the stops.
            case "section" -> sanitizeSection(item);
            default -> null;
        };
    }

    /**
     * Itinerary stops keep their location.
     *
     * <p>A stop is a public place or charging station the owner picked from a
     * provider search, not their address book, so its position is what makes
     * the published plan readable on a map and copyable as real stops. Day
     * anchors, which can be private, never pass through here.
     */
    private PublicItemDto sanitizePlace(PlannerItemDto item, PublicationOptions options) {
        PublicChargerDto charger = sanitizeCharger(item.evCharger());
        boolean located = isCoordinate(item.lat(), 90) && isCoordinate(item.lng(), 180);
        return new PublicItemDto(
                charger == null ? "place" : "charger",
                safeText(item.name()),
                safeText(item.description()),
                safeMediaUrl(item.imageUrl()),
                item.rating(),
                item.reviewCount(),
                safeText(item.time()),
                safeText(item.timeEnd()),
                options.includeBudget() ? item.cost() : null,
                options.includeNotes() ? safeText(item.notes()) : null,
                null,
                null,
                null,
                charger,
                located ? safeText(item.placeId()) : null,
                located ? safeText(item.address()) : null,
                located ? item.lat() : null,
                located ? item.lng() : null);
    }

    private boolean isCoordinate(Double value, double bound) {
        return value != null && Double.isFinite(value) && Math.abs(value) <= bound;
    }

    private PublicItemDto sanitizeSection(PlannerItemDto item) {
        String colorId = item.colorId() != null && item.colorId().matches("[a-z]{1,32}") ? item.colorId() : null;
        String city = item.destination() == null ? null : safeText(item.destination().name());
        return PublicItemDto.section(safeText(item.title()), colorId, city);
    }

    private PublicItemDto sanitizeNote(PlannerItemDto item) {
        return new PublicItemDto(
                "note", null, null, null, null, null, null, null, null, null,
                safeText(item.content()),
                null, null, null);
    }

    private PublicItemDto sanitizeChecklist(PlannerItemDto item) {
        List<String> labels = new ArrayList<>();
        for (PlannerChecklistSubItemDto subItem : nullSafe(item.items())) {
            if (subItem == null) {
                continue;
            }
            String label = safeText(subItem.label());
            if (label != null && !label.isBlank()) {
                // The label travels; subItem.checked() does not. Which boxes the
                // owner has ticked is their progress through the trip.
                labels.add(label);
            }
        }
        return new PublicItemDto(
                "checklist", null, null, null, null, null, null, null, null, null, null,
                safeText(item.title()),
                List.copyOf(labels),
                null);
    }

    /**
     * Keeps the station, drops the plan for it.
     *
     * <p>{@code targetBatteryPct}, {@code estimatedChargeMinutes}, the
     * auto/manual selection source and the lock flag are all outputs of the
     * optimiser, which ran over the day's real anchors. Publishing "charge 38
     * minutes here" alongside a redacted start location states how far away that
     * start was.
     */
    private PublicChargerDto sanitizeCharger(PlannerEvChargerDto charger) {
        if (charger == null) {
            return null;
        }
        List<String> connectors = new ArrayList<>();
        for (String connector : nullSafe(charger.connectorTypes())) {
            String safe = safeText(connector);
            if (safe != null && !safe.isBlank()) {
                connectors.add(safe);
            }
        }
        return new PublicChargerDto(
                List.copyOf(connectors),
                charger.maxKw(),
                charger.totalConnectors(),
                safeText(charger.priceText()),
                safeText(charger.openingHoursSummary()),
                safeText(charger.operatorName()));
    }

    private PublicBudgetDto sanitizeBudget(com.navio.tripplanningservice.dto.PlannerBudgetDto budget,java.util.Map<String,String> refs) {
        if (budget == null) {
            return null;
        }
        List<PublicBudgetDto.PublicExpenseDto> expenses = new ArrayList<>();
        for (PlannerExpenseDto expense : nullSafe(budget.expenses())) {
            if (expense == null) {
                continue;
            }
            expenses.add(new PublicBudgetDto.PublicExpenseDto(
                    safeText(expense.label()),
                    expense.amount(),
                    // expense.date() is not carried: an expense date is a travel
                    // date, and the dates option has to hold everywhere one is
                    // encoded, not just on the fields called "date".
                    safeText(expense.categoryId()), expenseRef(expense.id(),refs)));
        }
        return new PublicBudgetDto(
                Objects.requireNonNullElse(budget.currency(), CurrencyCode.THB),
                Objects.requireNonNullElse(budget.amount(), BigDecimal.ZERO),
                List.copyOf(expenses));
    }

    private void collectRefs(PlannerBlockDto source,PublicDayDto published,PublicationOptions options,java.util.Map<String,String> refs){
        int index=0;
        for(var item:nullSafe(source.items()))if(sanitizeItem(item,options)!=null){if(index<published.items().size())refs.put(item.id(),published.items().get(index++).id());}
    }
    private String expenseRef(String id,java.util.Map<String,String> refs){
        if(id==null)return null;
        if(id.startsWith("place-cost-"))return refs.get(id.substring(11));
        if(id.startsWith("place-expense-")){var encoded=id.substring(14).split("/",2)[0];try{return refs.get(java.net.URLDecoder.decode(encoded,java.nio.charset.StandardCharsets.UTF_8));}catch(IllegalArgumentException ignored){return null;}}
        return null;
    }

    private boolean isItineraryDay(PlannerBlockDto block) {
        return block.kind() != null && "itinerary".equalsIgnoreCase(block.kind().trim());
    }

    /** Mirrors {@code TripResponse}: name, else country, else city. */
    private String resolveTitle(Trip trip) {
        String name = safeText(trip.getDisplayName());
        if (name != null && !name.isBlank()) {
            return name;
        }
        String country = safeText(trip.getDestinationCountry());
        if (country != null && !country.isBlank()) {
            return country;
        }
        String city = safeText(trip.getDestinationCity());
        return city == null || city.isBlank() ? "Trip" : city;
    }

    /**
     * Only http(s) images reach a recipient.
     *
     * <p>Blocks {@code data:} and {@code javascript:} payloads, and blocks a
     * relative path, which in a recipient's browser would resolve against the
     * Navio origin and could pull a protected internal asset into a public page.
     */
    private String safeMediaUrl(String value) {
        String url = safeText(value);
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !ALLOWED_MEDIA_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
                return null;
            }
            return uri.getHost() == null ? null : url;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    static final int MAX_AUTHOR_NAME_LENGTH = 120;

    /**
     * Normalises the byline an owner publishes under.
     *
     * <p>One line, no control characters, at most {@value #MAX_AUTHOR_NAME_LENGTH}
     * code points; blank becomes null, which renders as "a Navio traveler". The
     * name is display text the owner chose, so nothing else about it is trusted
     * or interpreted.
     */
    public String sanitizeAuthorName(String value) {
        String text = safeText(value);
        if (text == null) {
            return null;
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        if (oneLine.isEmpty()) {
            return null;
        }
        if (oneLine.codePointCount(0, oneLine.length()) > MAX_AUTHOR_NAME_LENGTH) {
            oneLine = oneLine.substring(0, oneLine.offsetByCodePoints(0, MAX_AUTHOR_NAME_LENGTH)).trim();
        }
        return oneLine;
    }

    /** Trims and removes control characters, keeping newline and tab. */
    private String safeText(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (codePoint == '\n' || codePoint == '\t' || !Character.isISOControl(codePoint)) {
                builder.appendCodePoint(codePoint);
            }
        });
        return builder.toString().trim();
    }

    private <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
