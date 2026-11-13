package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.TripCoverResponse;
import com.navio.tripplanningservice.media.PictureException;
import com.navio.tripplanningservice.media.PictureValidator;
import com.navio.tripplanningservice.service.TripCoverService;
import com.navio.tripplanningservice.service.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TripCoverController.class)
class TripCoverControllerTests {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TRIP_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID COVER_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final MockMultipartFile FILE = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[] {1});

    @Autowired MockMvc mockMvc;
    @MockitoBean TripCoverService covers;

    @Test
    void anonymousCallersCannotUploadReadOrRemoveACover() throws Exception {
        mockMvc.perform(multipart("/v1/trips/{tripId}/cover", TRIP_ID).file(FILE))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/trips/{tripId}/cover", TRIP_ID))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/v1/trips/{tripId}/cover", TRIP_ID))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(covers);
    }

    @Test
    void uploadReturnsCreatedWithTheNewCoverId() throws Exception {
        when(covers.upload(eq(TRIP_ID), eq(USER_ID), any())).thenReturn(new TripCoverResponse(TRIP_ID, COVER_ID));

        mockMvc.perform(multipart("/v1/trips/{tripId}/cover", TRIP_ID).file(FILE).header("X-User-Id", USER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tripId").value(TRIP_ID.toString()))
                .andExpect(jsonPath("$.coverMediaId").value(COVER_ID.toString()));
    }

    @Test
    void uploadWithoutAFilePartIsABadRequest() throws Exception {
        mockMvc.perform(multipart("/v1/trips/{tripId}/cover", TRIP_ID).header("X-User-Id", USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("A picture file is required"));
        verifyNoInteractions(covers);
    }

    @Test
    void anInvalidPictureIsABadRequestWithItsReason() throws Exception {
        when(covers.upload(eq(TRIP_ID), eq(USER_ID), any()))
                .thenThrow(PictureException.invalid("Only PNG and JPEG pictures are supported"));

        mockMvc.perform(multipart("/v1/trips/{tripId}/cover", TRIP_ID).file(FILE).header("X-User-Id", USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Only PNG and JPEG pictures are supported"));
    }

    @Test
    void anOversizedUploadIsPayloadTooLarge() throws Exception {
        when(covers.upload(eq(TRIP_ID), eq(USER_ID), any())).thenThrow(new MaxUploadSizeExceededException(5));

        mockMvc.perform(multipart("/v1/trips/{tripId}/cover", TRIP_ID).file(FILE).header("X-User-Id", USER_ID))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.message").value("Picture must be at most 5 MiB"));
    }

    @Test
    void uploadingToSomeoneElsesTripIsNotFound() throws Exception {
        when(covers.upload(eq(TRIP_ID), eq(USER_ID), any())).thenThrow(new TripService.TripNotFoundException(TRIP_ID));

        mockMvc.perform(multipart("/v1/trips/{tripId}/cover", TRIP_ID).file(FILE).header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Trip not found"));
    }

    @Test
    void readServesTheImageWithoutCachingOrSniffing() throws Exception {
        when(covers.read(TRIP_ID, USER_ID)).thenReturn(new PictureValidator.Picture(new byte[] {7, 8}, "image/jpeg"));

        mockMvc.perform(get("/v1/trips/{tripId}/cover", TRIP_ID).header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(new byte[] {7, 8}))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void readingAMissingCoverIsNotFound() throws Exception {
        when(covers.read(TRIP_ID, USER_ID)).thenThrow(new PictureException(HttpStatus.NOT_FOUND, "Trip cover not found"));

        mockMvc.perform(get("/v1/trips/{tripId}/cover", TRIP_ID).header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Trip cover not found"));
    }

    @Test
    void removeReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/v1/trips/{tripId}/cover", TRIP_ID).header("X-User-Id", USER_ID))
                .andExpect(status().isNoContent());
        verify(covers).remove(TRIP_ID, USER_ID);
    }

    @Test
    void removingFromSomeoneElsesTripIsNotFound() throws Exception {
        doThrow(new TripService.TripNotFoundException(TRIP_ID)).when(covers).remove(TRIP_ID, USER_ID);

        mockMvc.perform(delete("/v1/trips/{tripId}/cover", TRIP_ID).header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
