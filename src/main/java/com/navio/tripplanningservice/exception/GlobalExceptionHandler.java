package com.navio.tripplanningservice.exception;

import com.navio.tripplanningservice.service.BlockItemService.BlockItemNotFoundException;
import com.navio.tripplanningservice.service.ListBlockService.ListBlockNotFoundException;
import com.navio.tripplanningservice.service.TripService.TripNotFoundException;
import com.navio.tripplanningservice.service.PlannerService.PlannerValidationException;
import com.navio.tripplanningservice.service.CurrencyConversionUnavailableException;
import com.navio.tripplanningservice.service.MobilityOptimizationUnavailableException;
import com.navio.tripplanningservice.service.TripEvOptimizationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(TripNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTripNotFound(TripNotFoundException ex) {
        log.error("Trip not found: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.NOT_FOUND.value())
                .message("Trip not found")
                .error(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(ListBlockNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleListBlockNotFound(ListBlockNotFoundException ex) {
        log.error("List block not found: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.NOT_FOUND.value())
                .message("List block not found")
                .error(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(BlockItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBlockItemNotFound(BlockItemNotFoundException ex) {
        log.error("Block item not found: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.NOT_FOUND.value())
                .message("Block item not found")
                .error(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Validation failed")
                .validationErrors(errors)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler({
            ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Request is invalid")
                .error(ex.getMessage())
                .build();
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(PlannerValidationException.class)
    public ResponseEntity<ErrorResponse> handlePlannerValidation(PlannerValidationException ex) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Planner snapshot is invalid")
                .error(ex.getMessage())
                .build();
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .message("The planner changed while it was being saved")
                .error("Refresh the planner and try again")
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(CurrencyConversionUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleCurrencyConversionUnavailable(
            CurrencyConversionUnavailableException ex) {
        log.warn("Currency conversion is unavailable: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .message("Currency conversion is temporarily unavailable")
                .error("Try again without changing the trip currency")
                .build();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }

    @ExceptionHandler(TripEvOptimizationException.class)
    public ResponseEntity<ErrorResponse> handleTripEvOptimization(TripEvOptimizationException ex) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .message("The EV route could not be optimized")
                .error(ex.getMessage())
                .build();
        return ResponseEntity.unprocessableEntity().body(errorResponse);
    }

    @ExceptionHandler(MobilityOptimizationUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleMobilityOptimizationUnavailable(
            MobilityOptimizationUnavailableException ex) {
        log.warn("Mobility EV optimization is unavailable: {}", ex.getMessage(), ex.getCause());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .message("EV route optimization is temporarily unavailable")
                .error("Try again in a moment")
                .build();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Planner write violated a database constraint", ex);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .message("Some place details could not be saved")
                .error("Remove or shorten the last place you added, then try again")
                .build();
        return ResponseEntity.unprocessableEntity().body(errorResponse);
    }

    @ExceptionHandler(com.navio.tripplanningservice.service.PlaceResolutionUnavailableException.class)
    public ResponseEntity<ErrorResponse> handlePlaceResolutionUnavailable(
            com.navio.tripplanningservice.service.PlaceResolutionUnavailableException ex) {
        log.warn("Place resolution is unavailable", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.builder()
                .timestamp(Instant.now()).status(503)
                .message("The destination could not be resolved")
                .error("Try again in a moment").build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("An unexpected error occurred")
                .error("Try again in a moment")
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
