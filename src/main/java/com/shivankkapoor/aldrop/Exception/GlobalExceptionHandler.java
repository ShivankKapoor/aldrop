package com.shivankkapoor.aldrop.Exception;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex){
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(PlatformNameTakenException.class)
    public ResponseEntity<Map<String, String>> handlePlatformNameTaken(PlatformNameTakenException ex){
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(PlatformNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePlatformNotFound(PlatformNotFoundException ex){
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(UsernameTakenException.class)
    public ResponseEntity<Map<String, String>> handleUsernameTaken(UsernameTakenException ex){
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCredentials(InvalidCredentialsException ex){
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InvalidSessionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidSession(InvalidSessionException ex){
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InvalidTotpException.class)
    public ResponseEntity<Map<String, String>> handleInvalidTotp(InvalidTotpException ex){
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(TotpNotAvailableException.class)
    public ResponseEntity<Map<String, String>> handleTotpNotAvailable(TotpNotAvailableException ex){
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(TotpAlreadyEnabledException.class)
    public ResponseEntity<Map<String, String>> handleTotpAlreadyEnabled(TotpAlreadyEnabledException ex){
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<Map<String, String>> handleTooManyAttempts(TooManyAttemptsException ex){
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DeviceBindingRequiredException.class)
    public ResponseEntity<Map<String, String>> handleDeviceBindingRequired(DeviceBindingRequiredException ex){
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException ex){
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "A record with a conflicting unique value already exists."));
    }
}
