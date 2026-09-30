package com.navio.tripplanningservice.dto.publication;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * The owner's view of their published link. Never served anonymously.
 *
 * @param published             false when this trip has no active link. Every
 *                              other field is then null and the dialog opens in
 *                              its unpublished state
 * @param token                 the shared secret; the client composes
 *                              {@code /share/plans/{token}} from it
 * @param hasUnpublishedChanges the source trip has moved since the snapshot was
 *                              frozen. Deliberately conservative — a private
 *                              note the owner added also trips it, because the
 *                              alternative is diffing sanitised output, and a
 *                              false "nothing to update" is the harmful
 *                              direction for a feature about what is visible
 * @param staleSanitizer        this snapshot was frozen by a sanitiser older
 *                              than the running one, so the link no longer
 *                              resolves. The owner must re-publish to restore
 *                              it under the current redaction rules
 * @param listedInExplore       the plan is also listed on the public Explore page
 * @param authorDisplayName     the byline currently shown on the published plan
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicationResponse(
        boolean published,
        String token,
        PublicationOptions options,
        Integer revision,
        Instant publishedAt,
        Instant updatedAt,
        Boolean hasUnpublishedChanges,
        Boolean staleSanitizer,
        Boolean listedInExplore,
        String authorDisplayName,
        String title
) {
    public PublicationResponse(boolean published, String token, PublicationOptions options,
            Integer revision, Instant publishedAt, Instant updatedAt,
            Boolean hasUnpublishedChanges, Boolean staleSanitizer, Boolean listedInExplore,
            String authorDisplayName) {
        this(published, token, options, revision, publishedAt, updatedAt,
                hasUnpublishedChanges, staleSanitizer, listedInExplore, authorDisplayName, null);
    }
    public static PublicationResponse notPublished() {
        return new PublicationResponse(false, null, null, null, null, null, null, null, null, null);
    }
}
