package com.juliashtal.devanalytics.exception;

public class GitHubException extends RuntimeException {
    public GitHubException(String message, Throwable e) {
        super(message, e);
    }
    public GitHubException(String message) {
        super(message);
    }
}
