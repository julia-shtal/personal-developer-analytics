package com.juliashtal.devanalytics.exception;

/**
 * Thrown when the caller lacks permission for an action (HTTP 403).
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
