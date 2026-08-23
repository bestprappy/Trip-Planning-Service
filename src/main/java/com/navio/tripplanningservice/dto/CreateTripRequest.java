package com.navio.tripplanningservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTripRequest {

    @NotBlank(message = "Display name is required")
    private String displayName;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotBlank(message = "Destination ID is required")
    private String destinationId;

    @NotBlank(message = "Destination name is required")
    private String destinationName;

    private Double destinationLat;

    private Double destinationLng;

    private String destinationCountry;
}
