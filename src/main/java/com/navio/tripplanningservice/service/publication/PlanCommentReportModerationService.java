package com.navio.tripplanningservice.service.publication;

import com.navio.tripplanningservice.dto.publication.CommentReportModeration.ReportedComment;
import com.navio.tripplanningservice.repository.PlanCommentReportRepository;
import com.navio.tripplanningservice.repository.PlanFeedbackRepository;
import com.navio.tripplanningservice.service.publication.PlanFeedbackService.FeedbackException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The platform moderation queue for reported Shared Plan comments.
 *
 * <p>Publishers still review reports on their own plans; this lets Navio's
 * moderators see every report too. The caller's roles come from the gateway's
 * {@code X-User-Roles}, which it strips from every inbound request and re-sets
 * from the validated token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PlanCommentReportModerationService {

    static final Set<String> MODERATOR_ROLES = Set.of("MODERATOR", "ADMIN", "OWNER");
    static final int MAX_PAGE_SIZE = 50;

    private final PlanCommentReportRepository reports;
    private final PlanFeedbackRepository feedback;

    public Page<ReportedComment> queue(String callerRoles, int page, int size) {
        requireModerator(callerRoles);
        var request = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        long total = reports.countOpen();
        var rows = total == 0 ? List.<ReportedComment>of()
                : reports.open(request.getPageSize(), request.getOffset());
        return new PageImpl<>(rows, request, total);
    }

    @Transactional
    public void resolve(String callerRoles, UUID moderator, UUID commentId, String decision) {
        requireModerator(callerRoles);
        var target = reports.openTarget(commentId)
                .orElseThrow(() -> new FeedbackException(404, "This report was already handled or the comment is gone."));
        switch (decision) {
            case "remove" -> feedback.delete(target.publicationId(), target.commentId());
            case "dismiss" -> reports.dismiss(target.commentId());
            default -> throw new FeedbackException(400, "Choose remove or dismiss.");
        }
        log.info("Plan comment report on {} resolved as {} by platform moderator {}", commentId, decision, moderator);
    }

    static void requireModerator(String callerRoles) {
        boolean moderator = callerRoles != null && Arrays.stream(callerRoles.split(","))
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .anyMatch(MODERATOR_ROLES::contains);
        if (!moderator) throw new FeedbackException(403, "Only platform moderators can review reports.");
    }
}
