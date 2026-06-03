package com.juliashtal.devanalytics.exception;

public class GitLabException extends RuntimeException {
    public GitLabException(String message) {
        super(message);
    }

    public GitLabException(String message, Throwable cause) {
        super(message, cause);
    }
}