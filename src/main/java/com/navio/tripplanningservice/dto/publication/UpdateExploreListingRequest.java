package com.navio.tripplanningservice.dto.publication;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Lists or unlists an already published plan on Explore, without re-publishing.
 *
 * <p>Takes effect immediately, unlike content options, which wait for Update.
 * Removing a plan from public discovery is something an owner should never have
 * to wait for, and it changes who can find the plan, not what they see.
 *
 * @param authorDisplayName refreshes the byline when present; null keeps the
 *                          stored one
 */
public record UpdateExploreListingRequest(
        @NotNull Boolean listedInExplore,
        @Size(max = 120, message = "authorDisplayName must be at most 120 characters")
        String authorDisplayName
) {
}
