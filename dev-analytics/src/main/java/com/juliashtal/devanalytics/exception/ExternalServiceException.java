package com.juliashtal.devanalytics.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an external service (GitHub, Jira, Ollama, etc.) is unavailable or returns an error
 * that cannot be recovered from at this call site.
 *
 * <p>Use {@link HttpStatus#BAD_GATEWAY} (502) when the upstream dependency returned an unexpected
 * response and {@link HttpStatus#SERVICE_UNAVAILABLE} (503) when it is temporarily down.</p>
 */
public class ExternalServiceException extends RuntimeException {

    private final HttpStatus httpStatus;

    public ExternalServiceException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public ExternalServiceException(String message, Throwable cause, HttpStatus httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
