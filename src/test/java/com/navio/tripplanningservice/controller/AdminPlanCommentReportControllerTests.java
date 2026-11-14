package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.publication.CommentReportModeration.ReportedComment;
import com.navio.tripplanningservice.repository.PlanCommentReportRepository;
import com.navio.tripplanningservice.repository.PlanCommentReportRepository.Target;
import com.navio.tripplanningservice.repository.PlanFeedbackRepository;
import com.navio.tripplanningservice.service.publication.PlanCommentReportModerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPlanCommentReportController.class)
@Import(PlanCommentReportModerationService.class)
class AdminPlanCommentReportControllerTests {

    private static final String PATH = "/v1/admin/plan-comment-reports";
    private static final UUID MODERATOR = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID COMMENT = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID PUBLICATION = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID AUTHOR = UUID.fromString("00000000-0000-4000-8000-000000000004");

    @Autowired MockMvc mvc;
    @MockitoBean PlanCommentReportRepository reports;
    @MockitoBean PlanFeedbackRepository feedback;

    @Test
    void travelersWithoutAModerationRoleCannotReadTheQueue() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(get(PATH).header("X-User-Roles", "USER")).andExpect(status().isForbidden());
        verifyNoInteractions(reports, feedback);
    }

    @Test
    void travelersWithoutAModerationRoleCannotResolveReports() throws Exception {
        mvc.perform(patch(PATH + "/" + COMMENT).header("X-User-Id", MODERATOR.toString()).header("X-User-Roles", "USER")
                .contentType("application/json").content("{\"decision\":\"remove\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reports, feedback);
    }

    @Test
    void moderatorsSeeEveryReportedCommentWithItsPlan() throws Exception {
        when(reports.countOpen()).thenReturn(1L);
        when(reports.open(20, 0)).thenReturn(List.of(new ReportedComment(COMMENT, "plan-token", "Kyoto in spring",
                "Buy followers here", AUTHOR, "Spammer", 2, List.of("HARASSMENT", "SPAM"),
                Instant.parse("2026-11-14T01:00:00Z"), Instant.parse("2026-11-14T02:00:00Z"))));

        mvc.perform(get(PATH).header("X-User-Roles", "moderator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].commentId").value(COMMENT.toString()))
                .andExpect(jsonPath("$.content[0].planToken").value("plan-token"))
                .andExpect(jsonPath("$.content[0].planTitle").value("Kyoto in spring"))
                .andExpect(jsonPath("$.content[0].reportCount").value(2))
                .andExpect(jsonPath("$.content[0].reasons[1]").value("SPAM"));
    }

    @Test
    void removingAReportedCommentHidesItForEveryone() throws Exception {
        when(reports.openTarget(COMMENT)).thenReturn(Optional.of(new Target(COMMENT, PUBLICATION)));

        mvc.perform(patch(PATH + "/" + COMMENT).header("X-User-Id", MODERATOR.toString()).header("X-User-Roles", "ADMIN")
                .contentType("application/json").content("{\"decision\":\"remove\"}"))
                .andExpect(status().isNoContent());

        verify(feedback).delete(PUBLICATION, COMMENT);
        verify(reports, never()).dismiss(any());
    }

    @Test
    void dismissingClearsTheReportsAndKeepsTheComment() throws Exception {
        when(reports.openTarget(COMMENT)).thenReturn(Optional.of(new Target(COMMENT, PUBLICATION)));

        mvc.perform(patch(PATH + "/" + COMMENT).header("X-User-Id", MODERATOR.toString()).header("X-User-Roles", "OWNER")
                .contentType("application/json").content("{\"decision\":\"dismiss\"}"))
                .andExpect(status().isNoContent());

        verify(reports).dismiss(COMMENT);
        verifyNoInteractions(feedback);
    }

    @Test
    void anAlreadyHandledReportIsNotFound() throws Exception {
        when(reports.openTarget(COMMENT)).thenReturn(Optional.empty());

        mvc.perform(patch(PATH + "/" + COMMENT).header("X-User-Id", MODERATOR.toString()).header("X-User-Roles", "ADMIN")
                .contentType("application/json").content("{\"decision\":\"dismiss\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        verify(reports, never()).dismiss(any());
    }

    @Test
    void rejectsAnUnknownDecisionBeforeTouchingReports() throws Exception {
        mvc.perform(patch(PATH + "/" + COMMENT).header("X-User-Id", MODERATOR.toString()).header("X-User-Roles", "ADMIN")
                .contentType("application/json").content("{\"decision\":\"ban\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.decision").exists());
        verifyNoInteractions(reports, feedback);
    }
}
