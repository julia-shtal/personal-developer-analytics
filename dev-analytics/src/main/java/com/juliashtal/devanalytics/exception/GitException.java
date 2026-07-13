package com.juliashtal.devanalytics.exception;

/**
 * Thrown when a local Git operation fails.
 */
public class GitException extends RuntimeException {
    public GitException(String message, Throwable e) {
        super(message, e);
    }
}
