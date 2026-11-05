package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.service.publication.PlanFeedbackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(PlanFeedbackController.class)
class PlanFeedbackControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean PlanFeedbackService feedback;
    @Test void mappedFeedbackErrorsRetainTheirStatusAndSafeMessage() throws Exception {
        for(int code:new int[]{400,401,403,404,409}){
            org.mockito.Mockito.when(feedback.summary(org.mockito.ArgumentMatchers.eq("token"),org.mockito.ArgumentMatchers.isNull()))
                .thenThrow(new PlanFeedbackService.FeedbackException(code,"Safe feedback error"));
            mvc.perform(get("/v1/shared-plans/token/feedback")).andExpect(status().is(code))
                .andExpect(jsonPath("$.message").value("Safe feedback error"));
        }
    }
    @Test void ratingRequiresGatewayIdentity() throws Exception {
        mvc.perform(put("/v1/shared-plans/token/feedback/rating").contentType("application/json").content("{\"score\":5}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(feedback);
    }
    @Test void validatesScoresAndCommentBodiesBeforeCallingService() throws Exception {
        String user="00000000-0000-0000-0000-000000000001";
        for (int score : new int[]{0,6}) mvc.perform(put("/v1/shared-plans/token/feedback/rating")
            .header("X-User-Id",user).contentType("application/json").content("{\"score\":"+score+"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/shared-plans/token/feedback/comments").header("X-User-Id",user)
            .contentType("application/json").content("{\"content\":\"   \"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(feedback);
    }
}
