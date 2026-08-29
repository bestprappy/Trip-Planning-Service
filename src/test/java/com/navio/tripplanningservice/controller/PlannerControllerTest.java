package com.navio.tripplanningservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.dto.PlannerBlockDto;
import com.navio.tripplanningservice.dto.PlannerBudgetDto;
import com.navio.tripplanningservice.dto.PlannerChecklistSubItemDto;
import com.navio.tripplanningservice.dto.PlannerExpenseDto;
import com.navio.tripplanningservice.dto.PlannerItemDto;
import com.navio.tripplanningservice.dto.PlannerSaveResponse;
import com.navio.tripplanningservice.dto.PlannerSnapshotRequest;
import com.navio.tripplanningservice.dto.PlannerSnapshotResponse;
import com.navio.tripplanningservice.service.PlannerService;
import com.navio.tripplanningservice.model.CurrencyCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlannerController.class)
class PlannerControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TRIP_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PlannerService plannerService;

    @Test
    void getsPlannerSnapshot() throws Exception {
        when(plannerService.getPlannerSnapshot(TRIP_ID, USER_ID))
                .thenReturn(snapshot());

        mockMvc.perform(get("/v1/trips/{tripId}/planner", TRIP_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks[0].id").value("block-list-1"))
                .andExpect(jsonPath("$.blocks[0].items[0].items[0].checked").value(true))
                .andExpect(jsonPath("$.budget.currency").value("THB"))
                .andExpect(jsonPath("$.budget.expenses[0].categoryId").value("food"))
                .andExpect(jsonPath("$.budget.expenses[0].date").doesNotExist());
    }

    @Test
    void savesPlannerSnapshot() throws Exception {
        PlannerSnapshotResponse snapshot = snapshot();
        PlannerSaveResponse acknowledgement = new PlannerSaveResponse(
                1L,
                Instant.parse("2026-08-23T00:01:00Z"));
        when(plannerService.savePlannerSnapshot(
                eq(TRIP_ID),
                eq(USER_ID),
                any(PlannerSnapshotRequest.class)))
                .thenReturn(acknowledgement);

        mockMvc.perform(put("/v1/trips/{tripId}/planner", TRIP_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlannerSnapshotRequest(
                                        snapshot.version(),
                                        snapshot.blocks(),
                                        snapshot.budget()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.savedAt").value("2026-08-23T00:01:00Z"))
                .andExpect(jsonPath("$.blocks").doesNotExist());
    }

    @Test
    void rejectsInvalidPlannerSnapshotBeforeCallingService() throws Exception {
        mockMvc.perform(put("/v1/trips/{tripId}/planner", TRIP_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[{\"kind\":\"list\",\"title\":\"\",\"date\":\"2026-09-01\",\"colorId\":\"teal\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    private PlannerSnapshotResponse snapshot() {
        PlannerChecklistSubItemDto subItem = new PlannerChecklistSubItemDto(
                "checklist-row-1",
                "Passport",
                true);
        PlannerItemDto checklist = new PlannerItemDto(
                "checklist-1",
                "checklist",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Documents",
                List.of(subItem));
        PlannerBlockDto block = new PlannerBlockDto(
                "block-list-1",
                "list",
                "Packing",
                LocalDate.of(2026, 9, 1),
                "amber",
                List.of(checklist));
        PlannerBudgetDto budget = new PlannerBudgetDto(
                CurrencyCode.THB,
                new BigDecimal("30000.00"),
                List.of(new PlannerExpenseDto(
                        "expense-dinner",
                        new BigDecimal("850.00"),
                        "Dinner",
                        "food",
                        null)));
        return new PlannerSnapshotResponse(
                List.of(block),
                budget,
                0L,
                Instant.parse("2026-08-23T00:00:00Z"));
    }
}
