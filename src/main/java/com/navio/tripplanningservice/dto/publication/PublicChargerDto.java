package com.navio.tripplanningservice.dto.publication;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A charging stop as itinerary information.
 *
 * <p>Carries what makes the stop useful to a reader — who operates it, how fast
 * it charges, what plugs it has. It carries no <em>projection</em>: no target
 * state of charge, no estimated charge minutes, no arrival or departure battery
 * percentage. Those are computed from the owner's vehicle, their battery level
 * and the day's real route, which includes anchors this snapshot removes, so
 * publishing them would either leak a redacted location or state a number that
 * is quietly wrong. Charge planning stays private in v1.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicChargerDto(
        List<String> connectorTypes,
        Double maxKw,
        Integer totalConnectors,
        String priceText,
        String openingHoursSummary,
        String operatorName
) {
}
