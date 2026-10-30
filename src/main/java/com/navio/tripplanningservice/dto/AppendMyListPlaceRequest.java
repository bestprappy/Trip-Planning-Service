package com.navio.tripplanningservice.dto;

import jakarta.validation.constraints.*;

public record AppendMyListPlaceRequest(
        @NotBlank @Size(max = 255) String providerPlaceId,
        @Pattern(regexp = "GOOGLE|SAVED_LOCATION") String provider,
        @Size(max = 160) String listBlockId,
        @NotNull @PositiveOrZero Long expectedVersion,
        @jakarta.validation.Valid SavedLocation savedLocation) {
    public AppendMyListPlaceRequest(String providerPlaceId,String provider,String listBlockId,Long expectedVersion) {
        this(providerPlaceId,provider,listBlockId,expectedVersion,null);
    }
    public record SavedLocation(@NotBlank @Size(max=255) String name,@Size(max=512) String address,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,@NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {}
}
