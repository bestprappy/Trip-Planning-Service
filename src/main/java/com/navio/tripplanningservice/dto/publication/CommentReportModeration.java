package com.navio.tripplanningservice.dto.publication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The platform moderators' view of reported Shared Plan comments. */
public final class CommentReportModeration {
    private CommentReportModeration() {}

    /**
     * One reported comment that is still visible, with every report on it folded together.
     *
     * @param planToken null once the plan is no longer shared, so moderators can still remove the comment
     */
    public record ReportedComment(UUID commentId, String planToken, String planTitle, String content,
            UUID authorAccountId, String authorName, long reportCount, List<String> reasons,
            Instant firstReportedAt, Instant lastReportedAt) {}

    /** {@code remove} hides the comment for everyone; {@code dismiss} clears its reports and keeps it. */
    public record ResolveRequest(
            @NotBlank @Pattern(regexp = "^(remove|dismiss)$", message = "decision must be remove or dismiss")
            String decision) {}
}
