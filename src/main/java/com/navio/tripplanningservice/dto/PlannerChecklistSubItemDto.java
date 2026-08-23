package com.navio.tripplanningservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlannerChecklistSubItemDto(
        @NotBlank @Size(max = 160) String id,
        @NotNull @Size(max = 500) String label,
        @NotNull Boolean checked
) {
}
