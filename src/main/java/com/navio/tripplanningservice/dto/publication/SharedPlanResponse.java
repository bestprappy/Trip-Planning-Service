package com.navio.tripplanningservice.dto.publication;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * What an anonymous recipient receives.
 *
 * <p>The snapshot plus when it was published, and nothing else. In particular no
 * trip id, no owner, no revision history and no indication that the plan has
 * changed since — a recipient learning that the owner edited their trip an hour
 * ago is a fact about the owner they were not given a link to.
 *
 * @param listedInExplore whether the owner also listed this plan on Explore. The
 *                        Explore detail page serves only listed plans, so a plan
 *                        taken off Explore stops appearing there immediately
 * @param authorName      the owner's byline as they published it, or null. A
 *                        display name only: never an account id or email
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SharedPlanResponse(
        PublicPlanSnapshot plan,
        Instant publishedAt,
        boolean listedInExplore,
        String authorName
) {
}
