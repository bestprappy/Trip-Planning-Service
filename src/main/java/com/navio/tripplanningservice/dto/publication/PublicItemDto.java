package com.navio.tripplanningservice.dto.publication;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One entry in a published day.
 *
 * <p>Compare with {@code PlannerItemDto}: {@code placeId}, {@code address},
 * {@code lat} and {@code lng} are absent by construction. The v1 recipient page
 * has no map, so coordinates have no reader to serve, and leaving the fields out
 * of the type means no future edit to the sanitiser can accidentally populate
 * them. {@code isVisited} is absent too — whether the owner has been somewhere
 * yet is their progress, not itinerary content.
 *
 * @param cost  null unless the owner opted into budget
 * @param notes null unless the owner opted into notes
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicItemDto(
        String type,
        String name,
        String description,
        String imageUrl,
        Double rating,
        Integer reviewCount,
        String time,
        String timeEnd,
        Double cost,
        String notes,
        String noteContent,
        String checklistTitle,
        List<String> checklistLabels,
        PublicChargerDto charger
) {
}
