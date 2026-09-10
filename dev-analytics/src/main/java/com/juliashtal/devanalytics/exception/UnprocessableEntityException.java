package com.juliashtal.devanalytics.exception;

/**
 * Thrown when a request is well-formed but names something an upstream system rejects
 * (HTTP 422) — a GitHub login that does not exist, for instance.
 *
 * <p>Distinct from {@link BadRequestException} (400, the value is malformed) and from
 * {@link NotFoundException} (404, the addressed resource is missing rather than the value in it).
 */
public class UnprocessableEntityException extends RuntimeException {
    public UnprocessableEntityException(String message) {
        super(message);
    }
}
