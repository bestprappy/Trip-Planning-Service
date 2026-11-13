package com.navio.tripplanningservice.dto;

import jakarta.validation.constraints.NotNull;

public record PlacePopularityPreference(@NotNull Boolean contributes) {}
