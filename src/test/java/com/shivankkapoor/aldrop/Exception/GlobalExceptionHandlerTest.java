package com.shivankkapoor.aldrop.Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsValidationErrorsToBadRequestWithFieldMessages() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("request", "username", "must not be blank");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleValidationErrors(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("username", "must not be blank");
    }

    @Test
    void mapsPlatformNameTakenToConflict() {
        ResponseEntity<Map<String, String>> response =
                handler.handlePlatformNameTaken(new PlatformNameTakenException("acme"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "Platform name already in use: acme");
    }

    @Test
    void mapsPlatformNotFoundToNotFound() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Map<String, String>> response =
                handler.handlePlatformNotFound(new PlatformNotFoundException(id));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Platform not found: " + id);
    }

    @Test
    void mapsUsernameTakenToConflict() {
        ResponseEntity<Map<String, String>> response =
                handler.handleUsernameTaken(new UsernameTakenException("alice"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "Username already in use: alice");
    }

    @Test
    void mapsInvalidCredentialsToUnauthorized() {
        ResponseEntity<Map<String, String>> response =
                handler.handleInvalidCredentials(new InvalidCredentialsException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Invalid username or password");
    }

    @Test
    void mapsInvalidSessionToUnauthorized() {
        ResponseEntity<Map<String, String>> response =
                handler.handleInvalidSession(new InvalidSessionException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Invalid or expired session");
    }

    @Test
    void mapsDataIntegrityViolationToConflict() {
        DataIntegrityViolationException ex =
                new DataIntegrityViolationException("duplicate key", new RuntimeException("root cause"));

        ResponseEntity<Map<String, String>> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody())
                .containsEntry("error", "A record with a conflicting unique value already exists.");
    }
}
