package com.juliashtal.devanalytics.exception;

/**
 * Thrown when a request conflicts with existing state (HTTP 409).
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
