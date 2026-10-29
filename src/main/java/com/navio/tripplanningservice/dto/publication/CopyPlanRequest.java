package com.navio.tripplanningservice.dto.publication;
import java.time.LocalDate;
public record CopyPlanRequest(String dateMode,LocalDate startDate) {}
