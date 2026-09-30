package com.navio.tripplanningservice.dto.publication;

import com.navio.tripplanningservice.model.CurrencyCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * The trip budget, present only when the owner opted in.
 *
 * <p>Expenses carry no date: an expense date is a travel date by another name,
 * and the dates option must hold across every field that encodes one.
 */
public record PublicBudgetDto(
        CurrencyCode currency,
        BigDecimal amount,
        List<PublicExpenseDto> expenses
) {
    public record PublicExpenseDto(
            String label,
            BigDecimal amount,
            String categoryId
    ) {
    }
}
