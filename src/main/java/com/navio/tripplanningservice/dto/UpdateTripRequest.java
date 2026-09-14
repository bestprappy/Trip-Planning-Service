package com.navio.tripplanningservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTripRequest {

    private String displayName;

    private LocalDate startDate;

    private LocalDate endDate;

    private String destinationId;


    private String visibility;
}
