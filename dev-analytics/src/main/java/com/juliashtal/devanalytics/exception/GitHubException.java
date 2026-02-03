package com.juliashtal.devanalytics.exception;

public class GitHubException extends RuntimeException {
    public GitHubException(String message, Exception e) {
        super(message);
    }
}
