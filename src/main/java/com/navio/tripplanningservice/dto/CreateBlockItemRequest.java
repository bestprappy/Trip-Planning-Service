package com.navio.tripplanningservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBlockItemRequest {

    @NotNull(message = "Item type is required")
    private String type;

    @NotBlank(message = "Title is required")
    private String title;

    private String notes;

    private Integer displayOrder;

    // ============ PLACE ITEM FIELDS ============
    private String placeId;
    private String placeName;
    private Double placeLat;
    private Double placeLng;
    private String placeType;
    private String placeAddress;
    private String placePhone;
    private String placeWebsite;
    private Double placeRating;
    private LocalTime estimatedTime;

    // ============ EV STATION FIELDS ============
    private String stationId;
    private String stationName;
    private String stationAddress;
    private Double stationLat;
    private Double stationLng;
    private String chargerType;
    private String connectorType;
    private Integer availablePlugs;
    private Double powerKw;
    private Integer estimatedChargeMinutes;
    private Double batteryPercentage;
    private String price;
    private String openingHours;
    private Boolean isCharged;

    // ============ CHECKLIST ITEM FIELDS ============
    private Boolean completed;

    // ============ RESERVATION FIELDS ============
    private String reservationId;
    private String reservationType;
    private String reservationDetails;
}
