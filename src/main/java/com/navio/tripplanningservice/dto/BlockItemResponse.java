package com.navio.tripplanningservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockItemResponse {

    private UUID id;

    private String type;

    private Integer displayOrder;

    private String title;

    private String notes;

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

    private Instant createdAt;

    private Instant updatedAt;
}
