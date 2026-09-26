package com.navio.tripplanningservice.dto.publication;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create or update a trip's published link.
 *
 * @param expectedTripVersion the {@code trip.version} the owner was looking at.
 *                            Required: publishing is a promise about specific
 *                            content, so a source that moved under the preview
 *                            must fail rather than publish something the owner
 *                            never reviewed
 * @param expectedRevision    the publication revision the owner was looking at,
 *                            or null for a first publish. Guards against a
 *                            second tab, and makes a double-clicked Update
 *                            idempotent rather than destructive
 * @param listInExplore       also list the plan on Explore. Null or absent means
 *                            no — discovery is never switched on by omission
 * @param authorDisplayName   the byline shown on the published plan, as the owner
 *                            saw it in the dialog. Null publishes without a name
 */
public record PublishPlanRequest(
        @NotNull Long expectedTripVersion,
        Integer expectedRevision,
        PublicationOptions options,
        Boolean listInExplore,
        @Size(max = 120, message = "authorDisplayName must be at most 120 characters")
        String authorDisplayName
) {
    public PublicationOptions safeOptions() {
        return PublicationOptions.orNone(options);
    }

    public boolean safeListInExplore() {
        return Boolean.TRUE.equals(listInExplore);
    }
}
