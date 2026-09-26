package com.navio.tripplanningservice.dto.publication;

/**
 * Where a published day starts or ends, as a recipient sees it.
 *
 * <p>This record deliberately has no {@code id}, {@code address}, {@code lat} or
 * {@code lng}. A published anchor is a name and nothing else, so there is no
 * field in which a home address could survive a mistake elsewhere in the
 * sanitiser. Recipients get "Bangkok Marriott", not a coordinate.
 *
 * @param name     the place name, or a fixed placeholder when {@code redacted}
 * @param redacted true when the owner's real anchor was personal and was replaced
 */
public record PublicAnchorDto(
        String name,
        boolean redacted
) {
    /** Shown in place of a {@code SAVED_PLACE} the owner starts the day at. */
    public static final String REDACTED_START = "Private start location";

    /** Shown in place of a {@code SAVED_PLACE} the owner ends the day at. */
    public static final String REDACTED_END = "Private end location";

    public static PublicAnchorDto redactedStart() {
        return new PublicAnchorDto(REDACTED_START, true);
    }

    public static PublicAnchorDto redactedEnd() {
        return new PublicAnchorDto(REDACTED_END, true);
    }

    public static PublicAnchorDto named(String name) {
        return new PublicAnchorDto(name, false);
    }
}
