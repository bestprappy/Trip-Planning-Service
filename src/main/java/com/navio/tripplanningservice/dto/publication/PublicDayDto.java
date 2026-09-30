package com.navio.tripplanningservice.dto.publication;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

/**
 * One published day.
 *
 * @param label     always present — "Day 1", "Day 2". The reader's spine when
 *                  the owner did not publish real dates
 * @param date      null unless the owner opted into travel dates. Removed from
 *                  the field, not merely hidden by the page
 * @param startsAt  null when the day inherits its start from the previous day.
 *                  A redacted anchor stays present and redacted rather than
 *                  becoming null, so the reader is told something was withheld
 *                  instead of silently seeing the neighbouring day's location
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicDayDto(
        String label,
        LocalDate date,
        String title,
        PublicAnchorDto startsAt,
        PublicAnchorDto endsAt,
        List<PublicItemDto> items
) {
}
