package com.navio.tripplanningservice.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    private Instant timestamp;

    private int status;

    private String message;

    private String error;

    private Map<String, String> validationErrors;
}
