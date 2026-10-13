package com.navio.tripplanningservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.navio.tripplanningservice.model.BlockItem;
import com.navio.tripplanningservice.service.PlannerService.PlannerValidationException;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * How each leg of a day is travelled. Mirrors the client's {@code leg-travel-mode.ts}: change
 * both together, and keep their shared cases in step.
 *
 * <p>A stop's saved {@code arrivalMode} decides the leg ending at it. Without one the leg is
 * automatic: public transit when both ends are stations, otherwise driving.
 */
final class LegTravelModes {

    static final String DRIVE = "drive";
    static final String TRANSIT = "transit";
    static final String WALK = "walk";
    static final String BIKE = "bike";
    static final String RAIL = "rail";
    static final String BUS = "bus";

    private static final Set<String> MODES = Set.of(DRIVE, TRANSIT, RAIL, BUS, BIKE, WALK);

    /** Google Places primary types of stops a public transit route can start or end at. */
    private static final Set<String> STATION_TYPES = Set.of(
            "subway_station", "train_station", "light_rail_station", "transit_station");

    /** Fallback for stops saved before the type was kept: the display label, e.g. "Subway Station". */
    private static final Pattern STATION_LABEL = Pattern.compile(
            "\\b(subway|metro|mrt|bts|train|railway|rail|light rail|transit)\\s+station\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PLACE_TYPE = Pattern.compile("[a-z0-9_]{1,100}");

    private LegTravelModes() {
    }

    /**
     * Applies a requested arrival mode: omitted keeps the saved mode, JSON null resets it to
     * automatic, anything else must be a known mode on a place.
     */
    static void applyArrivalMode(BlockItem item, String itemType, JsonNode requested) {
        if (requested == null) {
            return;
        }
        if (requested.isNull()) {
            item.setArrivalMode(null);
            item.setArrivalRouteKey(null);
            return;
        }
        if (!"place".equals(itemType) || !requested.isTextual() || !MODES.contains(requested.asText())) {
            throw new PlannerValidationException("A stop's travel mode must be drive, transit, rail, bus, bike or walk");
        }
        if (!java.util.Objects.equals(item.getArrivalMode(), requested.asText())) item.setArrivalRouteKey(null);
        item.setArrivalMode(requested.asText());
    }

    static void applyArrivalRouteKey(BlockItem item, String itemType, JsonNode requested) {
        if (requested == null) return;
        if (requested.isNull()) { item.setArrivalRouteKey(null); return; }
        if (!"place".equals(itemType) || !Set.of(TRANSIT, RAIL, BUS).contains(java.util.Objects.requireNonNullElse(item.getArrivalMode(), ""))
                || !requested.isTextual() || !isValidRouteKey(requested.asText())) {
            throw new PlannerValidationException("A selected transit route must have a valid line and station sequence on a transit place");
        }
        item.setArrivalRouteKey(requested.asText());
    }

    private static boolean isValidRouteKey(String value) {
        if (value.length() > 4096 || !value.startsWith("v1:")) return false;
        try {
            JsonNode rides = new com.fasterxml.jackson.databind.ObjectMapper().readTree(value.substring(3));
            if (rides == null || !rides.isArray() || rides.isEmpty() || rides.size() > 16) return false;
            for (JsonNode ride : rides) {
                if (!ride.isArray() || ride.size() != 5) return false;
                for (JsonNode field : ride) if (!field.isNull() && (!field.isTextual() || field.asText().length() > 200)) return false;
            }
            return true;
        } catch (java.io.IOException exception) { return false; }
    }

    /** Keeps a provider type only when it is a plain identifier; a stop never loses a known type. */
    static void applyPlaceType(BlockItem item, String requested) {
        if (requested != null && PLACE_TYPE.matcher(requested).matches()) {
            item.setPlaceType(requested);
        }
    }

    /**
     * The mode of the leg from {@code from} to {@code to}. A day's start or end anchor is passed
     * as {@code null}: anchors are never stations and carry no saved mode.
     */
    static String resolve(BlockItem from, BlockItem to) {
        if (to != null && to.getArrivalMode() != null && MODES.contains(to.getArrivalMode())) {
            return to.getArrivalMode();
        }
        return isStation(from) && isStation(to) ? TRANSIT : DRIVE;
    }

    static boolean isStation(BlockItem item) {
        if (item == null) {
            return false;
        }
        String type = item.getPlaceType();
        if (type != null && STATION_TYPES.contains(type.toLowerCase(Locale.ROOT))) {
            return true;
        }
        String label = item.getPlaceDescription();
        return label != null && STATION_LABEL.matcher(label).find();
    }
}
