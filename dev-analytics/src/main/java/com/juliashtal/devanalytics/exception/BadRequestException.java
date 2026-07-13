package com.juliashtal.devanalytics.exception;

/**
 * Thrown when a request is malformed or fails validation (HTTP 400).
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
