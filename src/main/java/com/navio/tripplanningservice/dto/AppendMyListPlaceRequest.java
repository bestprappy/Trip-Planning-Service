package com.navio.tripplanningservice.dto;

import jakarta.validation.constraints.*;

public record AppendMyListPlaceRequest(
        @NotBlank @Size(max = 255) String providerPlaceId,
        @Pattern(regexp = "GOOGLE") String provider,
        @Size(max = 160) String listBlockId,
        @NotNull @PositiveOrZero Long expectedVersion) {}
