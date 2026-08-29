package com.navio.tripplanningservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlannerExpenseDto(
        @NotBlank @Size(max = 160) String id,
        @NotNull @DecimalMin(value = "0.0", inclusive = false)
        @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 255) String label,
        @NotBlank @Size(max = 50) String categoryId,
        LocalDate date
) {
}
