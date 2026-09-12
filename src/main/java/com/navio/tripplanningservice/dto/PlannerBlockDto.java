package com.navio.tripplanningservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record PlannerBlockDto(
        @NotBlank @Size(max = 160) String id,
        @NotBlank String kind,
        @NotNull @Size(max = 255) String title,
        @NotNull LocalDate date,
        @NotBlank @Size(max = 50) String colorId,
        List<@Valid PlannerItemDto> items,
        @Valid PlannerDestinationDto destination,
        /** Set only when this day does not start where the previous one ended. */
        @Valid PlannerAnchorDto startAnchor,
        @Valid PlannerAnchorDto endAnchor
) {
    public PlannerBlockDto(String id, String kind, String title, LocalDate date,
                           String colorId, List<PlannerItemDto> items) {
        this(id, kind, title, date, colorId, items, null, null, null);
    }

    public PlannerBlockDto(String id, String kind, String title, LocalDate date,
                           String colorId, List<PlannerItemDto> items,
                           PlannerDestinationDto destination) {
        this(id, kind, title, date, colorId, items, destination, null, null);
    }
}
