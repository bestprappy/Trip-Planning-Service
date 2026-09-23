package com.navio.tripplanningservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlannerItemDto(
        @NotBlank @Size(max = 160) String id,
        @NotBlank String type,
        String placeId,
        String name,
        String description,
        String address,
        Double lat,
        Double lng,
        Double rating,
        Integer reviewCount,
        String imageUrl,
        String notes,
        Boolean isVisited,
        String time,
        String timeEnd,
        Double cost,
        @Valid PlannerEvChargerDto evCharger,
        String content,
        String title,
        List<@Valid PlannerChecklistSubItemDto> items,
        com.fasterxml.jackson.databind.JsonNode observedSocCheckpoint
) {
    public PlannerItemDto(String id, String type, String placeId, String name, String description, String address,
        Double lat, Double lng, Double rating, Integer reviewCount, String imageUrl, String notes, Boolean isVisited,
        String time, String timeEnd, Double cost, PlannerEvChargerDto evCharger, String content, String title,
        List<PlannerChecklistSubItemDto> items) {
        this(id,type,placeId,name,description,address,lat,lng,rating,reviewCount,imageUrl,notes,isVisited,time,timeEnd,cost,evCharger,content,title,items,null);
    }
}
