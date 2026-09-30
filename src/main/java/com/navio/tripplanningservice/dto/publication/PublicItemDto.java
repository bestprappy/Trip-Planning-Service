package com.navio.tripplanningservice.dto.publication;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One entry in a published day.
 *
 * <p>Place and charger stops carry their provider id, address and coordinates so
 * the plan renders on a map and copies as real stops; notes and checklists never
 * do. {@code isVisited} is excluded because it records
 * the owner's progress rather than itinerary content.
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
        PublicChargerDto charger,
        String placeId,
        String address,
        Double lat,
        Double lng
) {
    public PublicItemDto(String type, String name, String description, String imageUrl,
                         Double rating, Integer reviewCount, String time, String timeEnd,
                         Double cost, String notes, String noteContent, String checklistTitle,
                         List<String> checklistLabels, PublicChargerDto charger) {
        this(type, name, description, imageUrl, rating, reviewCount, time, timeEnd,
                cost, notes, noteContent, checklistTitle, checklistLabels, charger,
                null, null, null, null);
    }
}
