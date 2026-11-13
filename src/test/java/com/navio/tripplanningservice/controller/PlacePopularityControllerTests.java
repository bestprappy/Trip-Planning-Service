package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.PlacePopularityPreference;
import com.navio.tripplanningservice.dto.PlaceRecommendationsResponse;
import com.navio.tripplanningservice.exception.GlobalExceptionHandler;
import com.navio.tripplanningservice.service.PlacePopularityService;
import com.navio.tripplanningservice.service.PlannerService;
import com.navio.tripplanningservice.service.TripService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PlacePopularityControllerTests {
    @Mock PlacePopularityService service;
    MockMvc mvc;
    final UUID user=UUID.randomUUID(), trip=UUID.randomUUID();
    String recommendations(){ return "/v1/trips/"+trip+"/place-recommendations?lat=13.7&lng=100.5"; }
    final String preference="/v1/trips/place-popularity/preferences";
    @BeforeEach void setup(){ mvc=MockMvcBuilders.standaloneSetup(new PlacePopularityController(service)).setControllerAdvice(new GlobalExceptionHandler()).build(); }

    @Test void allRoutesRequireAuthenticatedIdentity() throws Exception {
        mvc.perform(get(recommendations())).andExpect(status().isBadRequest());
        mvc.perform(get(preference)).andExpect(status().isBadRequest());
        mvc.perform(put(preference).contentType(MediaType.APPLICATION_JSON).content("{\"contributes\":false}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void returnsBoundedAggregateEnvelopeWithoutCaching() throws Exception {
        when(service.recommendations(trip,user,13.7,100.5)).thenReturn(new PlaceRecommendationsResponse(List.of(),5));
        mvc.perform(get(recommendations()).header("X-User-Id",user)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.minimumTravelers").value(5));
    }
    @Test void foreignTripIsNotFound() throws Exception {
        when(service.recommendations(any(),any(),anyDouble(),anyDouble())).thenThrow(new TripService.TripNotFoundException(trip));
        mvc.perform(get(recommendations()).header("X-User-Id",user)).andExpect(status().isNotFound());
    }
    @Test void invalidCoordinatesMapToBadRequest() throws Exception {
        when(service.recommendations(any(),any(),anyDouble(),anyDouble())).thenThrow(new PlannerService.PlannerValidationException("Invalid destination"));
        mvc.perform(get(recommendations()).header("X-User-Id",user)).andExpect(status().isBadRequest());
    }
    @Test void rejectsMissingOrNonBooleanPreferences() throws Exception {
        for(String body:new String[]{"{}","{\"contributes\":null}","{\"contributes\":{}}"})
            mvc.perform(put(preference).header("X-User-Id",user).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void preferenceReadAndWriteUseOnlyAuthenticatedUser() throws Exception {
        when(service.preference(user)).thenReturn(new PlacePopularityPreference(true));
        when(service.setPreference(user,false)).thenReturn(new PlacePopularityPreference(false));
        mvc.perform(get(preference).header("X-User-Id",user)).andExpect(status().isOk()).andExpect(jsonPath("$.contributes").value(true));
        mvc.perform(put(preference).header("X-User-Id",user).contentType(MediaType.APPLICATION_JSON).content("{\"contributes\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contributes").value(false));
    }
}
