package com.navio.tripplanningservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.dto.publication.PublicationOptions;
import com.navio.tripplanningservice.dto.publication.PublicationResponse;
import com.navio.tripplanningservice.dto.publication.PublishPlanRequest;
import com.navio.tripplanningservice.service.TripService;
import com.navio.tripplanningservice.service.publication.TripPublicationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TripPublicationController.class)
class TripPublicationControllerTests {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-00000000000a");
    private static final UUID TRIP_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TripPublicationService publicationService;

    // ------------------------------------------------- caller must be identified

    /**
     * This service has no security config of its own: the gateway strips any
     * client-supplied identity header and re-injects {@code X-User-Id} from a
     * validated JWT. A request arriving here without one therefore did not come
     * through an authenticated path, and must not reach the service.
     */
    @Test
    void refusesToManageALinkForAnUnidentifiedCaller() throws Exception {
        mockMvc.perform(get("/v1/trips/{tripId}/publication", TRIP_ID))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(publicationService);
    }

    @Test
    void refusesToPublishForAnUnidentifiedCaller() throws Exception {
        mockMvc.perform(put("/v1/trips/{tripId}/publication", TRIP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publishRequest())))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(publicationService);
    }

    @Test
    void refusesToPreviewForAnUnidentifiedCaller() throws Exception {
        mockMvc.perform(post("/v1/trips/{tripId}/publication/preview", TRIP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"options\":{\"includeDates\":true}}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(publicationService);
    }

    @Test
    void refusesToStopSharingForAnUnidentifiedCaller() throws Exception {
        mockMvc.perform(delete("/v1/trips/{tripId}/publication", TRIP_ID))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(publicationService);
    }

    // ---------------------------------------------------------------- ownership

    @Test
    void reportsAnotherUsersTripAsNotFoundRatherThanForbidden() throws Exception {
        when(publicationService.getPublication(TRIP_ID, USER_ID))
                .thenThrow(new TripService.TripNotFoundException(TRIP_ID));

        mockMvc.perform(get("/v1/trips/{tripId}/publication", TRIP_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Trip not found"));
    }

    // ------------------------------------------------------------- happy paths

    @Test
    void returnsTheOwnersCurrentLink() throws Exception {
        when(publicationService.getPublication(TRIP_ID, USER_ID)).thenReturn(new PublicationResponse(
                true, "tok-abc", new PublicationOptions(true, false, false), 2,
                Instant.parse("2026-09-20T10:00:00Z"), Instant.parse("2026-09-21T10:00:00Z"),
                false, false, true, "Kanya S."));

        mockMvc.perform(get("/v1/trips/{tripId}/publication", TRIP_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.token").value("tok-abc"))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.options.includeDates").value(true))
                .andExpect(jsonPath("$.options.includeBudget").value(false))
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(false))
                .andExpect(jsonPath("$.listedInExplore").value(true));
    }

    // ---------------------------------------------------------- explore listing

    @Test
    void refusesToChangeTheExploreListingForAnUnidentifiedCaller() throws Exception {
        mockMvc.perform(patch("/v1/trips/{tripId}/publication", TRIP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listedInExplore\":true}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(publicationService);
    }

    @Test
    void passesTheCallerIntoTheServiceSoOwnershipIsCheckedThere() throws Exception {
        when(publicationService.updateExploreListing(TRIP_ID, USER_ID, false, "Kanya S."))
                .thenReturn(PublicationResponse.notPublished());

        mockMvc.perform(patch("/v1/trips/{tripId}/publication", TRIP_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listedInExplore\":false,\"authorDisplayName\":\"Kanya S.\"}"))
                .andExpect(status().isOk());

        verify(publicationService).updateExploreListing(TRIP_ID, USER_ID, false, "Kanya S.");
    }

    @Test
    void rejectsAnAuthorNameLongerThanAProfileNameCanBe() throws Exception {
        mockMvc.perform(put("/v1/trips/{tripId}/publication", TRIP_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedTripVersion\":3,\"authorDisplayName\":\"" + "x".repeat(121) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.authorDisplayName").exists());

        verifyNoInteractions(publicationService);
    }

    /** Listing is never switched on (or off) by a body that does not say which. */
    @Test
    void rejectsAListingChangeThatDoesNotSayWhichWay() throws Exception {
        mockMvc.perform(patch("/v1/trips/{tripId}/publication", TRIP_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.listedInExplore").exists());

        verifyNoInteractions(publicationService);
    }

    @Test
    void mapsListingAnUnpublishedPlanToA409() throws Exception {
        when(publicationService.updateExploreListing(TRIP_ID, USER_ID, true, null))
                .thenThrow(new TripPublicationService.PublicationConflictException(
                        "Publish this plan before listing it on Explore"));

        mockMvc.perform(patch("/v1/trips/{tripId}/publication", TRIP_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listedInExplore\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Publish this plan before listing it on Explore"));
    }

    @Test
    void readsAnAbsentListingFlagOnPublishAsUnlisted() throws Exception {
        ArgumentCaptor<PublishPlanRequest> captured = ArgumentCaptor.forClass(PublishPlanRequest.class);
        when(publicationService.publish(eq(TRIP_ID), eq(USER_ID), any(PublishPlanRequest.class)))
                .thenReturn(PublicationResponse.notPublished());

        mockMvc.perform(put("/v1/trips/{tripId}/publication", TRIP_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedTripVersion\":3}"))
                .andExpect(status().isOk());

        verify(publicationService).publish(eq(TRIP_ID), eq(USER_ID), captured.capture());
        assertThat(captured.getValue().safeListInExplore()).isFalse();
    }

    @Test
    void reportsAnUnpublishedTripWithoutInventingAToken() throws Exception {
        when(publicationService.getPublication(TRIP_ID, USER_ID))
                .thenReturn(PublicationResponse.notPublished());

        mockMvc.perform(get("/v1/trips/{tripId}/publication", TRIP_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void stoppingSharingAnswersNoContent() throws Exception {
        mockMvc.perform(delete("/v1/trips/{tripId}/publication", TRIP_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ errors

    @Test
    void mapsAPublishConflictToA409TheDialogCanActOn() throws Exception {
        when(publicationService.publish(eq(TRIP_ID), eq(USER_ID), any(PublishPlanRequest.class)))
                .thenThrow(new TripPublicationService.PublicationConflictException(
                        "This plan changed since you opened the preview"));

        mockMvc.perform(put("/v1/trips/{tripId}/publication", TRIP_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publishRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("This plan changed since you opened the preview"))
                .andExpect(jsonPath("$.error").value(
                        "Refresh the plan and review what will be shared before publishing"));
    }

    /**
     * Publishing without saying which version is being published is rejected
     * rather than defaulted, because a default would mean "publish whatever is
     * there now" — the opposite of the promise the preview made.
     */
    @Test
    void rejectsAPublishThatNamesNoSourceVersion() throws Exception {
        mockMvc.perform(put("/v1/trips/{tripId}/publication", TRIP_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"options\":{\"includeDates\":false}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));

        verifyNoInteractions(publicationService);
    }

    /**
     * A client that sends only the option it changed must not be rejected, and
     * the options it omitted must read as off.
     *
     * <p>Jackson maps an absent JSON property onto a record's primitive boolean
     * as null and fails the whole request, so before {@code PublicationOptions.of}
     * existed this returned a 400 that looked like a malformed body.
     */
    @Test
    void acceptsPartialOptionsAndTreatsTheOmittedOnesAsOff() throws Exception {
        ArgumentCaptor<PublishPlanRequest> captured = ArgumentCaptor.forClass(PublishPlanRequest.class);
        when(publicationService.publish(eq(TRIP_ID), eq(USER_ID), any(PublishPlanRequest.class)))
                .thenReturn(PublicationResponse.notPublished());

        mockMvc.perform(put("/v1/trips/{tripId}/publication", TRIP_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedTripVersion\":3,\"options\":{\"includeNotes\":true}}"))
                .andExpect(status().isOk());

        verify(publicationService).publish(eq(TRIP_ID), eq(USER_ID), captured.capture());
        assertThat(captured.getValue().safeOptions())
                .isEqualTo(new PublicationOptions(false, true, false));
    }

    /** An entirely absent options object publishes the least, not the most. */
    @Test
    void treatsAnAbsentOptionsObjectAsEverythingOff() throws Exception {
        ArgumentCaptor<PublishPlanRequest> captured = ArgumentCaptor.forClass(PublishPlanRequest.class);
        when(publicationService.publish(eq(TRIP_ID), eq(USER_ID), any(PublishPlanRequest.class)))
                .thenReturn(PublicationResponse.notPublished());

        mockMvc.perform(put("/v1/trips/{tripId}/publication", TRIP_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedTripVersion\":3}"))
                .andExpect(status().isOk());

        verify(publicationService).publish(eq(TRIP_ID), eq(USER_ID), captured.capture());
        assertThat(captured.getValue().safeOptions()).isEqualTo(PublicationOptions.none());
    }

    private PublishPlanRequest publishRequest() {
        return new PublishPlanRequest(3L, null, PublicationOptions.none(), false, null);
    }
}
