package com.navio.tripplanningservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripResponse {

    private UUID id;

    private String displayName;

    private LocalDate startDate;

    private LocalDate endDate;

    private String destinationId;

    private String destinationName;

    private Double destinationLat;

    private Double destinationLng;

    private String destinationCountry;

    private String destinationCity;

    private String destinationRegion;

    private String destinationCountryCode;

    public String getTitle() {
        return displayName != null ? displayName
                : destinationCity != null ? destinationCity : destinationCountry;
    }

    private String visibility;

    private Instant createdAt;

    private Instant updatedAt;
}
