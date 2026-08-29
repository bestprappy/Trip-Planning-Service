package com.navio.tripplanningservice.dto;

import com.navio.tripplanningservice.model.CurrencyCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record PlannerBudgetDto(
        @NotNull CurrencyCode currency,
        @NotNull @DecimalMin("0.0") @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotNull List<@Valid PlannerExpenseDto> expenses
) {
}
