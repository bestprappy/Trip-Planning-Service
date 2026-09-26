package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.publication.ExplorePlanSummary;
import com.navio.tripplanningservice.dto.publication.PublicAnchorDto;
import com.navio.tripplanningservice.dto.publication.PublicDayDto;
import com.navio.tripplanningservice.dto.publication.PublicItemDto;
import com.navio.tripplanningservice.dto.publication.PublicPlanSnapshot;
import com.navio.tripplanningservice.dto.publication.PublicationOptions;
import com.navio.tripplanningservice.dto.publication.SharedPlanResponse;
import com.navio.tripplanningservice.service.publication.PlanPublicationSanitizer;
import com.navio.tripplanningservice.service.publication.TripPublicationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(SharedPlanController.class)
class SharedPlanControllerTests {

    private static final String TOKEN = "tok-3f9a2b";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TripPublicationService publicationService;

    @Test
    void servesAPublishedPlanToACallerWithNoIdentityAtAll() throws Exception {
        when(publicationService.readSharedPlan(TOKEN)).thenReturn(sharedPlan());

        mockMvc.perform(get("/v1/shared-plans/{token}", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.title").value("Songkran road trip"))
                .andExpect(jsonPath("$.plan.days[0].label").value("Day 1"))
                .andExpect(jsonPath("$.plan.days[0].items[0].name").value("Wat Arun"))
                .andExpect(jsonPath("$.authorName").value("Kanya S."));
    }

    /**
     * A shared plan must not sit in a CDN, a corporate proxy or a browser's disk
     * cache: revoking the link has to take effect on the next request, and a
     * cached copy outlives the owner's decision.
     */
    @Test
    void forbidsCachingSoRevocationTakesEffectImmediately() throws Exception {
        when(publicationService.readSharedPlan(TOKEN)).thenReturn(sharedPlan());

        mockMvc.perform(get("/v1/shared-plans/{token}", TOKEN))
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void asksSearchEnginesNotToIndexAnUnlistedPlan() throws Exception {
        when(publicationService.readSharedPlan(TOKEN)).thenReturn(sharedPlan());

        mockMvc.perform(get("/v1/shared-plans/{token}", TOKEN))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"))
                // The token is in the path, so a referrer header would hand the
                // secret to every site the recipient clicks through to.
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void answersADeadLinkWithoutNamingTheOwnerOrTheTrip() throws Exception {
        when(publicationService.readSharedPlan(TOKEN))
                .thenThrow(new TripPublicationService.SharedPlanUnavailableException());

        mockMvc.perform(get("/v1/shared-plans/{token}", TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("This shared plan is no longer available"))
                .andExpect(jsonPath("$.validationErrors").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Songkran"))));
    }

    /**
     * The anonymous prefix is read-only. If a write verb ever starts resolving
     * here, the gateway rule that permits GET on this path stops being the whole
     * story.
     */
    @Test
    void exposesNoWriteVerbsOnTheAnonymousPrefix() throws Exception {
        mockMvc.perform(post("/v1/shared-plans/{token}", TOKEN))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/v1/shared-plans/{token}", TOKEN))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/v1/shared-plans/{token}", TOKEN))
                .andExpect(status().isMethodNotAllowed());
    }

    private SharedPlanResponse sharedPlan() {
        PublicItemDto item = new PublicItemDto(
                "place", "Wat Arun", "A landmark", null, 4.6, 12000,
                "09:00", "11:00", null, null, null, null, null, null);
        PublicDayDto day = new PublicDayDto(
                "Day 1", null, "Bangkok",
                PublicAnchorDto.redactedStart(),
                PublicAnchorDto.named("Bangkok Marriott"),
                List.of(item));
        PublicPlanSnapshot plan = new PublicPlanSnapshot(
                PlanPublicationSanitizer.SANITIZER_VERSION,
                "Songkran road trip", "Bangkok", "Thailand",
                null, null, 1, List.of(day), null, PublicationOptions.none());
        return new SharedPlanResponse(plan, Instant.parse("2026-09-20T10:00:00Z"), false, "Kanya S.");
    }

    // ------------------------------------------------------------ explore feed

    @Test
    void servesTheExploreFeedToACallerWithNoIdentity() throws Exception {
        ExplorePlanSummary summary = new ExplorePlanSummary(
                "tok-listed", "Songkran road trip", "Kanya S.", "Bangkok", "Thailand", 2, 3, 1, null,
                List.of("Wat Arun"),
                List.of(new ExplorePlanSummary.ExploreDayShape(List.of("place", "charger"))),
                Instant.parse("2026-09-20T10:00:00Z"), Instant.parse("2026-09-20T10:00:00Z"));
        when(publicationService.listExplorePlans(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/v1/shared-plans"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.content[0].token").value("tok-listed"))
                .andExpect(jsonPath("$.content[0].authorName").value("Kanya S."))
                .andExpect(jsonPath("$.content[0].days[0].stops[1]").value("charger"))
                .andExpect(jsonPath("$.content[0].chargerCount").value(1));
    }

    /** One anonymous request must not be able to pull the whole feed. */
    @Test
    void capsTheFeedPageSizeForAnonymousCallers() throws Exception {
        ArgumentCaptor<Pageable> captured = ArgumentCaptor.forClass(Pageable.class);
        when(publicationService.listExplorePlans(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

        mockMvc.perform(get("/v1/shared-plans").param("size", "5000").param("page", "-3"))
                .andExpect(status().isOk());

        verify(publicationService).listExplorePlans(captured.capture());
        assertThat(captured.getValue().getPageSize()).isEqualTo(SharedPlanController.MAX_PAGE_SIZE);
        assertThat(captured.getValue().getPageNumber()).isZero();
    }

    @Test
    void exposesNoWriteVerbsOnTheFeed() throws Exception {
        mockMvc.perform(post("/v1/shared-plans"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/v1/shared-plans"))
                .andExpect(status().isMethodNotAllowed());
    }
}
