package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.publication.CommentReportModeration.ReportedComment;
import com.navio.tripplanningservice.dto.publication.CommentReportModeration.ResolveRequest;
import com.navio.tripplanningservice.service.publication.PlanCommentReportModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Reported Shared Plan comments, for platform moderators. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/plan-comment-reports")
public class AdminPlanCommentReportController {

    private final PlanCommentReportModerationService moderation;

    @GetMapping
    public ResponseEntity<Page<ReportedComment>> queue(
            @RequestHeader(name = "X-User-Roles", required = false) String roles,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(moderation.queue(roles, page, size));
    }

    @PatchMapping("/{commentId}")
    public ResponseEntity<Void> resolve(
            @RequestHeader(name = "X-User-Roles", required = false) String roles,
            @RequestHeader("X-User-Id") UUID moderator,
            @PathVariable UUID commentId, @Valid @RequestBody ResolveRequest request) {
        moderation.resolve(roles, moderator, commentId, request.decision());
        return ResponseEntity.noContent().build();
    }
}
