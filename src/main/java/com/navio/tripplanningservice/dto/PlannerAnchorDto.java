package com.navio.tripplanningservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Where a day starts or ends.
 *
 * <p>{@code kind} is not decoration. It decides what happens when the trip is
 * shared: a {@code SAVED_PLACE} came from the owner's personal address book and
 * must be stripped from any published snapshot, while {@code PLACE} and
 * {@code MANUAL} are itinerary content that travels with the template.
 *
 * @param id   identifier within the kind's namespace — the saved place id, the
 *             provider place id, or a client-generated id for a manual pin
 * @param kind {@code SAVED_PLACE}, {@code PLACE} or {@code MANUAL}
 */
public record PlannerAnchorDto(
        @NotBlank @Size(max = 512) String id,
        @NotBlank @Pattern(regexp = "SAVED_PLACE|PLACE|MANUAL",
                message = "kind must be SAVED_PLACE, PLACE or MANUAL") String kind,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 512) String address,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng
) {
    /** True when this anchor must not leave the owner's account. */
    public boolean isPersonal() {
        return "SAVED_PLACE".equals(kind);
    }
}
