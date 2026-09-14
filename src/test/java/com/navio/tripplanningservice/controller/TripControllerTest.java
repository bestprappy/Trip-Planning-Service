package com.navio.tripplanningservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.dto.CreateTripRequest;
import com.navio.tripplanningservice.dto.TripResponse;
import com.navio.tripplanningservice.dto.UpdateTripRequest;
import com.navio.tripplanningservice.service.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TripController.class)
class TripControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TRIP_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TripService tripService;

    @Test
    void createsTripForHeaderUser() throws Exception {
        when(tripService.createTrip(eq(USER_ID), any(CreateTripRequest.class)))
                .thenReturn(tripResponse("Bangkok trip"));

        mockMvc.perform(post("/v1/trips")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TRIP_ID.toString()))
                .andExpect(jsonPath("$.displayName").value("Bangkok trip"))
                .andExpect(jsonPath("$.destinationName").value("Bangkok, Thailand"));
    }

    @Test
    void rejectsCreateWithoutUserHeader() throws Exception {
        mockMvc.perform(post("/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidCreatePayload() throws Exception {
        CreateTripRequest invalid = CreateTripRequest.builder()
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 4))
                .build();

        mockMvc.perform(post("/v1/trips")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void returnsOwnedTrip() throws Exception {
        when(tripService.getTripById(TRIP_ID, USER_ID))
                .thenReturn(tripResponse("Bangkok trip"));

        mockMvc.perform(get("/v1/trips/{tripId}", TRIP_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TRIP_ID.toString()));
    }

    @Test
    void returnsNotFoundWhenTripIsNotOwned() throws Exception {
        when(tripService.getTripById(TRIP_ID, USER_ID))
                .thenThrow(new TripService.TripNotFoundException(TRIP_ID));

        mockMvc.perform(get("/v1/trips/{tripId}", TRIP_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Trip not found"));
    }

    @Test
    void updatesTripMetadata() throws Exception {
        when(tripService.updateTrip(eq(TRIP_ID), eq(USER_ID), any(UpdateTripRequest.class)))
                .thenReturn(tripResponse("Bangkok production trip"));

        mockMvc.perform(put("/v1/trips/{tripId}", TRIP_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Bangkok production trip\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Bangkok production trip"));
    }

    @Test
    void deletesOwnedTrip() throws Exception {
        doNothing().when(tripService).deleteTrip(TRIP_ID, USER_ID);

        mockMvc.perform(delete("/v1/trips/{tripId}", TRIP_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    void acceptsCreationWithoutDisplayName() throws Exception {
        when(tripService.createTrip(eq(USER_ID), any(CreateTripRequest.class)))
                .thenReturn(TripResponse.builder().destinationCity("Bangkok").destinationCountryCode("TH").build());
        var request = createRequest();
        request.setDisplayName(null);
        mockMvc.perform(post("/v1/trips").header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Bangkok"))
                .andExpect(jsonPath("$.destinationCountryCode").value("TH"));
    }

    @Test
    void reportsResolutionFailureWithoutLeakingProviderDetails() throws Exception {
        when(tripService.createTrip(eq(USER_ID), any(CreateTripRequest.class)))
                .thenThrow(new com.navio.tripplanningservice.service.PlaceResolutionUnavailableException("secret provider details"));
        mockMvc.perform(post("/v1/trips").header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Try again in a moment"));
    }

    private CreateTripRequest createRequest() {
        return CreateTripRequest.builder()
                .displayName("Bangkok trip")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 4))
                .destinationId("google:bangkok-thailand")
                .build();
    }

    private TripResponse tripResponse(String displayName) {
        return TripResponse.builder()
                .id(TRIP_ID)
                .displayName(displayName)
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 4))
                .destinationId("google:bangkok-thailand")
                .destinationName("Bangkok, Thailand")
                .destinationLat(13.7563)
                .destinationLng(100.5018)
                .destinationCountry("Thailand")
                .visibility("PRIVATE")
                .createdAt(Instant.parse("2026-08-23T00:00:00Z"))
                .updatedAt(Instant.parse("2026-08-23T00:00:00Z"))
                .build();
    }
}
