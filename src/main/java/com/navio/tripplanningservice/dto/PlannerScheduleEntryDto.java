package com.navio.tripplanningservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One moment inside a stop's visit, such as "09:00-09:10 Check in". Times are HH:mm. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlannerScheduleEntryDto(
        @NotBlank @Size(max = 64) String id,
        @NotBlank @Size(max = 5) String time,
        @Size(max = 5) String timeEnd,
        @NotNull @Size(max = 200) String label
) {
}
