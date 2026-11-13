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
                : destinationCountry != null ? destinationCountry : destinationCity;
    }

    private String visibility;

    /** Current owner-uploaded cover, or null when the planner shows the destination photo. */
    private UUID coverMediaId;

    private Instant createdAt;

    private Instant updatedAt;
}
