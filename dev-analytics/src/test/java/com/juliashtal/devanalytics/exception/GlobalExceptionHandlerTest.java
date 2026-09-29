package com.juliashtal.devanalytics.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}: one test per handler method, pinning the
 * HTTP status and the {@link ApiError} body each exception maps to.
 *
 * <p>The handler does not extend {@code ResponseEntityExceptionHandler}, so Spring's own
 * request-binding failures only get a status other than 500 if this class declares one.</p>
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock HttpServletRequest request;

    @BeforeEach
    void stubPath() {
        when(request.getRequestURI()).thenReturn("/api/teams/5/export");
    }

    @Test
    void handleValidation_fieldErrors_joinsMessagesAndReturns400() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("obj", "name", "must not be blank"),
                new FieldError("obj", "type", "must not be null")));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

        ResponseEntity<ApiError> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("Validation Error");
        assertThat(response.getBody().getMessage()).isEqualTo("must not be blank; must not be null");
    }

    @Test
    void handleConstraintViolation_violations_joinsPathsAndMessagesAndReturns400() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("limit");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be positive");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ApiError> response = handler.handleConstraintViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("limit: must be positive");
    }

    @Test
    void handleNotReadable_malformedBody_returns400WithGenericMessage() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("boom");

        ResponseEntity<ApiError> response = handler.handleNotReadable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Malformed or unreadable request body");
    }

    @Test
    void handleMissingParameter_missingRequestParam_returns400NamingTheParameter() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("from", "LocalDate");

        ResponseEntity<ApiError> response = handler.handleMissingParameter(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Required parameter 'from' is missing");
        assertThat(response.getBody().getPath()).isEqualTo("/api/teams/5/export");
    }

    @Test
    void handleIllegalArgument_returns400WithTheExceptionMessage() {
        ResponseEntity<ApiError> response =
                handler.handleIllegalArgument(new IllegalArgumentException("bad value"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("bad value");
    }

    @Test
    void handleBadRequest_returns400WithTheExceptionMessage() {
        ResponseEntity<ApiError> response =
                handler.handleBadRequest(new BadRequestException("malformed"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("malformed");
    }

    @Test
    void handleBadCredentials_returns401WithTheExceptionMessage() {
        ResponseEntity<ApiError> response =
                handler.handleBadCredentials(new BadCredentialsException("Invalid credentials"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid credentials");
    }

    @Test
    void handleAccessDenied_returns403WithAGenericMessage() {
        ResponseEntity<ApiError> response =
                handler.handleAccessDenied(new AccessDeniedException("denied"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("You do not have permission to access this resource");
    }

    @Test
    void handleUnprocessable_returns422WithTheExceptionMessage() {
        ResponseEntity<ApiError> response = handler.handleUnprocessable(
                new UnprocessableEntityException("no such GitHub login"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getMessage()).isEqualTo("no such GitHub login");
    }

    @Test
    void handleConflict_returns409WithTheExceptionMessage() {
        ResponseEntity<ApiError> response =
                handler.handleConflict(new ConflictException("already exists"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("already exists");
    }

    @Test
    void handleForbidden_returns403WithTheExceptionMessage() {
        ResponseEntity<ApiError> response =
                handler.handleForbidden(new ForbiddenException("not the owner"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("not the owner");
    }

    @Test
    void handleNotFound_noSuchElementException_returns404() {
        ResponseEntity<ApiError> response =
                handler.handleNotFound(new NoSuchElementException("DataSource not found: 5"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("DataSource not found: 5");
    }

    @Test
    void handleNotFound_notFoundException_returns404() {
        ResponseEntity<ApiError> response =
                handler.handleNotFound(new NotFoundException("Team", 5L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Team not found with id: 5");
    }

    @Test
    void handleNoResource_returns404NamingTheRequestUri() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/api/nope");

        ResponseEntity<ApiError> response = handler.handleNoResource(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("No endpoint found for /api/teams/5/export");
    }

    @Test
    void handleRateLimit_returns429WithRetryAfterHeader() {
        ResponseEntity<ApiError> response =
                handler.handleRateLimit(new RateLimitExceededException(30L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("30");
        assertThat(response.getBody().getMessage()).isEqualTo("Rate limit exceeded. Retry after 30 seconds.");
    }

    @Test
    void handleExternalService_usesTheExceptionsOwnStatus() {
        ExternalServiceException ex = new ExternalServiceException(
                "Ollama unavailable", HttpStatus.SERVICE_UNAVAILABLE);

        ResponseEntity<ApiError> response = handler.handleExternalService(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getError()).isEqualTo("Service Unavailable");
        assertThat(response.getBody().getMessage()).isEqualTo("Ollama unavailable");
    }

    @Test
    void handleGitHub_returns502WithTheExceptionMessage() {
        ResponseEntity<ApiError> response =
                handler.handleGitHub(new GitHubException("rate limited"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getError()).isEqualTo("GitHub Error");
        assertThat(response.getBody().getMessage()).isEqualTo("rate limited");
    }

    @Test
    void handleGit_returns500WithAGenericMessage_neverLeakingTheCause() {
        GitException ex = new GitException("clone failed", new RuntimeException("disk full"));

        ResponseEntity<ApiError> response = handler.handleGit(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage())
                .isEqualTo("A Git operation failed — see server logs for details");
    }

    @Test
    void handleJira_returns502WithTheExceptionMessage() {
        ResponseEntity<ApiError> response =
                handler.handleJira(new JiraException("project not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getError()).isEqualTo("Jira Error");
        assertThat(response.getBody().getMessage()).isEqualTo("project not found");
    }

    @Test
    void handleGeneral_returns500WithAGenericMessage_neverLeakingTheCause() {
        ResponseEntity<ApiError> response =
                handler.handleGeneral(new GeneralException("internal state invalid"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage())
                .isEqualTo("An internal error occurred — see server logs for details");
    }

    @Test
    void handleUnexpected_returns500WithAGenericMessage_neverLeakingTheCause() {
        ResponseEntity<ApiError> response =
                handler.handleUnexpected(new RuntimeException("npe somewhere"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage())
                .isEqualTo("An unexpected error occurred — see server logs for details");
    }
}
