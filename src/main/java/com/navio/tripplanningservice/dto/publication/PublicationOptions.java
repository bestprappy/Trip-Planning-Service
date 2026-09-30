package com.navio.tripplanningservice.dto.publication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What the owner chose to include beyond the bare itinerary.
 *
 * <p>Every option is off unless the owner turned it on. {@link #none()} is the
 * value used whenever options are missing or unreadable, so a bug that loses the
 * settings publishes less rather than more.
 *
 * <p>Itinerary content — day order, place names, charging stops — is not
 * optional; it is what a shared plan is. Personal locations are not optional
 * either, in the other direction: no flag can publish a {@code SAVED_PLACE}.
 */
public record PublicationOptions(
        boolean includeDates,
        boolean includeNotes,
        boolean includeBudget
) {
    /**
     * Reads absent JSON fields as "off" instead of rejecting the request.
     *
     * <p>Jackson maps a missing property onto a record's primitive {@code boolean}
     * as null and fails, so without this a client sending only the option it
     * wanted to change would get a 400. Boxing here and treating null as false
     * keeps the omission meaning what it reads as — an option the owner did not
     * turn on — and keeps the default in one place rather than relying on every
     * caller to send all three.
     */
    @JsonCreator
    public static PublicationOptions of(
            @JsonProperty("includeDates") Boolean includeDates,
            @JsonProperty("includeNotes") Boolean includeNotes,
            @JsonProperty("includeBudget") Boolean includeBudget) {
        return new PublicationOptions(
                Boolean.TRUE.equals(includeDates),
                Boolean.TRUE.equals(includeNotes),
                Boolean.TRUE.equals(includeBudget));
    }

    public static PublicationOptions none() {
        return new PublicationOptions(false, false, false);
    }

    /** Null-safe: an absent options record is the closed one, never the open one. */
    public static PublicationOptions orNone(PublicationOptions options) {
        return options == null ? none() : options;
    }
}
