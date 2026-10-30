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
 * <p>A {@code section} divider carries its title in {@code name}, its colour id and
 * the name of its city. The city is a public place name, never coordinates.
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
        Double lng,
        String arrivalMode,
        String arrivalRouteKey,
        String placeType,
        String id,
        List<com.navio.tripplanningservice.dto.PlannerScheduleEntryDto> schedule,
        String timeSource,
        String colorId,
        String city
) {
    public PublicItemDto(String type,String name,String description,String imageUrl,Double rating,Integer reviewCount,
        String time,String timeEnd,Double cost,String notes,String noteContent,String checklistTitle,List<String> checklistLabels,
        PublicChargerDto charger,String placeId,String address,Double lat,Double lng,String arrivalMode,String arrivalRouteKey,String placeType){
        this(type,name,description,imageUrl,rating,reviewCount,time,timeEnd,cost,notes,noteContent,checklistTitle,checklistLabels,charger,placeId,address,lat,lng,arrivalMode,arrivalRouteKey,placeType,null,null,null,null,null);
    }
    public PublicItemDto withDetails(String publicId,List<com.navio.tripplanningservice.dto.PlannerScheduleEntryDto> moments,String source){
        return new PublicItemDto(type,name,description,imageUrl,rating,reviewCount,time,timeEnd,cost,notes,noteContent,checklistTitle,checklistLabels,charger,placeId,address,lat,lng,arrivalMode,arrivalRouteKey,placeType,publicId,moments,source,colorId,city);
    }
    public PublicItemDto(String type, String name, String description, String imageUrl,
                         Double rating, Integer reviewCount, String time, String timeEnd,
                         Double cost, String notes, String noteContent, String checklistTitle,
                         List<String> checklistLabels, PublicChargerDto charger,
                         String placeId, String address, Double lat, Double lng) {
        this(type,name,description,imageUrl,rating,reviewCount,time,timeEnd,cost,notes,noteContent,
                checklistTitle,checklistLabels,charger,placeId,address,lat,lng,null,null,null);
    }

    public PublicItemDto withTransport(String mode, String routeKey, String publicPlaceType) {
        return new PublicItemDto(type,name,description,imageUrl,rating,reviewCount,time,timeEnd,cost,notes,
                noteContent,checklistTitle,checklistLabels,charger,placeId,address,lat,lng,mode,routeKey,publicPlaceType,id,schedule,timeSource,colorId,city);
    }

    public static PublicItemDto section(String title, String colorId, String city) {
        return new PublicItemDto("section", title, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, colorId, city);
    }

    public PublicItemDto(String type, String name, String description, String imageUrl,
                         Double rating, Integer reviewCount, String time, String timeEnd,
                         Double cost, String notes, String noteContent, String checklistTitle,
                         List<String> checklistLabels, PublicChargerDto charger) {
        this(type, name, description, imageUrl, rating, reviewCount, time, timeEnd,
                cost, notes, noteContent, checklistTitle, checklistLabels, charger,
                null, null, null, null);
    }
}
