package com.juliashtal.devanalytics.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>The handler does not extend {@code ResponseEntityExceptionHandler}, so Spring's own
 * request-binding failures only get a status other than 500 if this class declares one.</p>
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock HttpServletRequest request;

    @Test
    void handleMissingParameter_missingRequestParam_returns400NamingTheParameter() {
        when(request.getRequestURI()).thenReturn("/api/teams/5/export");
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("from", "LocalDate");

        ResponseEntity<ApiError> response = handler.handleMissingParameter(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Required parameter 'from' is missing");
        assertThat(response.getBody().getPath()).isEqualTo("/api/teams/5/export");
    }
}
