package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.*;
import com.navio.tripplanningservice.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.navio.tripplanningservice.exception.GlobalExceptionHandler;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MyListPlaceControllerTests {
    MockMvc mvc;
    @Mock MyListPlaceService service;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new MyListPlaceController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    final UUID trip = UUID.randomUUID(), user = UUID.randomUUID();
    final String body = """
            {"providerPlaceId":"google-1","provider":"GOOGLE","expectedVersion":4}
            """;
    String path() { return "/v1/trips/" + trip + "/planner/my-list/places"; }

    @Test void appendsAndReturnsAcknowledgedVersion() throws Exception {
        when(service.append(eq(trip), eq(user), any())).thenReturn(new AppendMyListPlaceResponse("list", "item", 5, false));
        mvc.perform(post(path()).header("X-User-Id", user).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(5))
                .andExpect(jsonPath("$.blockId").value("list"));
    }
    @Test void requiresIdentityHeader() throws Exception {
        mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void requiresVersionAndRejectsUnsupportedProvider() throws Exception {
        for (String invalid : new String[]{"{\"providerPlaceId\":\"p\"}",
                "{\"providerPlaceId\":\"p\",\"provider\":\"OTHER\",\"expectedVersion\":4}",
                "{\"providerPlaceId\":\" \",\"expectedVersion\":4}"}) {
            mvc.perform(post(path()).header("X-User-Id", user).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
    @Test void hidesAnotherOwnersTrip() throws Exception {
        when(service.append(any(), any(), any())).thenThrow(new TripService.TripNotFoundException(trip));
        mvc.perform(post(path()).header("X-User-Id", user).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }
    @Test void staleVersionReturnsConflict() throws Exception {
        when(service.append(any(), any(), any())).thenThrow(new ObjectOptimisticLockingFailureException("Trip", trip));
        mvc.perform(post(path()).header("X-User-Id", user).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }
    @Test void selectionRequiredReturnsBadRequest() throws Exception {
        when(service.append(any(), any(), any())).thenThrow(new PlannerService.PlannerValidationException("Choose a My List"));
        mvc.perform(post(path()).header("X-User-Id", user).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
    @Test void providerFailureReturnsUnavailable() throws Exception {
        when(service.append(any(), any(), any())).thenThrow(new PlaceResolutionUnavailableException("Unavailable"));
        mvc.perform(post(path()).header("X-User-Id", user).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isServiceUnavailable());
    }
}
