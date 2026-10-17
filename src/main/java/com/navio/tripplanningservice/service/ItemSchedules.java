package com.navio.tripplanningservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.navio.tripplanningservice.dto.PlannerScheduleEntryDto;
import com.navio.tripplanningservice.model.BlockItem;
import com.navio.tripplanningservice.service.PlannerService.PlannerValidationException;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** The moments a traveller plans inside one stop's visit, stored as JSON on the stop. */
@Slf4j
final class ItemSchedules {

    static final int MAX_ENTRIES = 40;
    static final int MAX_LABEL_LENGTH = 200;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<PlannerScheduleEntryDto>> ENTRIES = new TypeReference<>() {
    };
    private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    private ItemSchedules() {
    }

    /**
     * Stores the requested schedule. An older client omits it, so omitted keeps the saved one;
     * an empty list clears it. Only places have a schedule.
     */
    static void apply(BlockItem item, String itemType, List<PlannerScheduleEntryDto> requested) {
        if (!"place".equals(itemType)) {
            item.setSchedule(null);
            return;
        }
        if (requested == null) {
            return;
        }
        validate(requested);
        item.setSchedule(requested.isEmpty() ? null : write(requested));
    }

    /** The saved schedule, or {@code null} when the stop has none or it cannot be read. */
    static List<PlannerScheduleEntryDto> read(BlockItem item) {
        String stored = item.getSchedule();
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(stored, ENTRIES);
        } catch (JsonProcessingException exception) {
            log.warn("Ignoring an unreadable schedule on block item {}", item.getId(), exception);
            return null;
        }
    }

    private static void validate(List<PlannerScheduleEntryDto> entries) {
        if (entries.size() > MAX_ENTRIES) {
            throw new PlannerValidationException("A stop can have at most " + MAX_ENTRIES + " scheduled times");
        }
        Set<String> ids = new HashSet<>();
        for (PlannerScheduleEntryDto entry : entries) {
            if (entry == null || entry.id() == null || entry.id().isBlank() || !ids.add(entry.id())) {
                throw new PlannerValidationException("Each scheduled time in a stop needs its own id");
            }
            if (!isTime(entry.time()) || (entry.timeEnd() != null && !isTime(entry.timeEnd()))) {
                throw new PlannerValidationException("Scheduled times must use HH:mm format");
            }
            if (entry.label() == null || entry.label().length() > MAX_LABEL_LENGTH) {
                throw new PlannerValidationException(
                        "A scheduled time's label must be at most " + MAX_LABEL_LENGTH + " characters");
            }
        }
    }

    private static boolean isTime(String value) {
        return value != null && TIME.matcher(value).matches();
    }

    private static String write(List<PlannerScheduleEntryDto> entries) {
        try {
            return JSON.writeValueAsString(entries);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not store a stop's schedule", exception);
        }
    }
}
