package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.model.BlockItem;
import com.navio.tripplanningservice.service.PlannerService.PlannerValidationException;

import java.time.LocalTime;
import java.util.Objects;
import java.util.Set;

/** Whether a stop's time was set by the traveller or by Calculate times. */
final class ItemTimeSources {

    static final String FIXED = "fixed";
    static final String CALCULATED = "calculated";

    private static final Set<String> SOURCES = Set.of(FIXED, CALCULATED);

    private ItemTimeSources() {
    }

    /**
     * The source to store for the incoming times. An older client omits it; the saved source then
     * survives only while the times are unchanged, so an edit it makes can never stay "calculated"
     * and be overwritten by the next calculation.
     */
    static String resolve(BlockItem item, String itemType, String requested, LocalTime start, LocalTime end) {
        if (!"place".equals(itemType) || (start == null && end == null)) {
            return null;
        }
        if (requested != null) {
            if (!SOURCES.contains(requested)) {
                throw new PlannerValidationException("A stop's time source must be fixed or calculated");
            }
            return requested;
        }
        return Objects.equals(item.getStartTime(), start) && Objects.equals(item.getEndTime(), end)
                ? item.getTimeSource()
                : null;
    }
}
