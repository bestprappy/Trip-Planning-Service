package com.navio.tripplanningservice.controller;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.navio.tripplanningservice.config.LegacyJsonNodeModule;
import com.navio.tripplanningservice.dto.PlannerBlockDto;
import com.navio.tripplanningservice.dto.PlannerBudgetDto;
import com.navio.tripplanningservice.dto.PlannerItemDto;
import com.navio.tripplanningservice.dto.PlannerSaveResponse;
import com.navio.tripplanningservice.dto.PlannerSnapshotRequest;
import com.navio.tripplanningservice.dto.PlannerSnapshotResponse;
import com.navio.tripplanningservice.model.CurrencyCode;
import com.navio.tripplanningservice.service.PlannerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The planner's free-form JSON fields must cross the HTTP boundary as plain JSON. */
@WebMvcTest(PlannerController.class)
@Import(LegacyJsonNodeModule.class)
class PlannerJsonContractTests {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TRIP_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlannerService plannerService;

    @Test
    void writesAnEmptyEnergyStateAsJsonNullRatherThanTheNodesInternals() throws Exception {
        when(plannerService.getPlannerSnapshot(TRIP_ID, USER_ID)).thenReturn(snapshot(JSON.nullNode()));

        mockMvc.perform(get("/v1/trips/{tripId}/planner", TRIP_ID).header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energyState").value(nullValue()))
                .andExpect(jsonPath("$.energyState.nodeType").doesNotExist());
    }

    @Test
    void writesSavedEnergyStateObservationsAndTravelModesAsPlainJson() throws Exception {
        var energy = JSON.objectNode().put("initialSocPct", new BigDecimal("72.25"));
        when(plannerService.getPlannerSnapshot(TRIP_ID, USER_ID)).thenReturn(snapshot(energy));

        mockMvc.perform(get("/v1/trips/{tripId}/planner", TRIP_ID).header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energyState.initialSocPct").value(72.25))
                .andExpect(jsonPath("$.blocks[0].items[0].observedSocCheckpoint.socPct").value(68))
                .andExpect(jsonPath("$.blocks[0].items[0].arrivalMode").value("transit"))
                .andExpect(jsonPath("$.blocks[0].items[1].observedSocCheckpoint").value(nullValue()))
                .andExpect(jsonPath("$.blocks[0].items[1].arrivalMode").doesNotExist());
    }

    @Test
    void readsAnExplicitNullAsClearAndAnOmittedFieldAsKeep() throws Exception {
        when(plannerService.savePlannerSnapshot(eq(TRIP_ID), eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PlannerSaveResponse(2L, Instant.EPOCH));

        mockMvc.perform(put("/v1/trips/{tripId}/planner", TRIP_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 1, "blocks": [{"id": "day-1", "kind": "itinerary", "title": "Day 1",
                                  "date": "2026-10-20", "colorId": "teal", "items": [
                                    {"id": "a", "type": "place", "placeId": "p", "name": "A", "address": "",
                                     "lat": 25.0, "lng": 121.5, "arrivalMode": "transit",
                                     "observedSocCheckpoint": null}]}]}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<PlannerSnapshotRequest> sent = ArgumentCaptor.forClass(PlannerSnapshotRequest.class);
        verify(plannerService).savePlannerSnapshot(eq(TRIP_ID), eq(USER_ID), sent.capture());
        PlannerItemDto item = sent.getValue().blocks().getFirst().items().getFirst();
        assertThat(item.arrivalMode().asText()).isEqualTo("transit");
        assertThat(item.observedSocCheckpoint()).isNotNull();
        assertThat(item.observedSocCheckpoint().isNull()).isTrue();
        assertThat(sent.getValue().energyState()).isNull();
    }

    private PlannerSnapshotResponse snapshot(com.fasterxml.jackson.databind.JsonNode energyState) {
        PlannerItemDto observed = new PlannerItemDto("a", "place", "p-a", "A", null, "", 25.0, 121.5,
                null, null, null, null, false, null, null, null, null, null, null, null,
                JSON.objectNode().put("socPct", 68), "subway_station", JSON.textNode("transit"));
        PlannerItemDto plain = new PlannerItemDto("b", "place", "p-b", "B", null, "", 25.1, 121.6,
                null, null, null, null, false, null, null, null, null, null, null, null,
                JSON.nullNode(), null, null);
        PlannerBlockDto day = new PlannerBlockDto("day-1", "itinerary", "Day 1", LocalDate.of(2026, 10, 20),
                "teal", List.of(observed, plain));
        return new PlannerSnapshotResponse(List.of(day),
                new PlannerBudgetDto(CurrencyCode.THB, BigDecimal.ZERO, List.of()),
                1L, Instant.EPOCH, List.of("trip-energy-v1"), energyState);
    }
}
