package com.juliashtal.devanalytics.exception;

public class GitHubException extends RuntimeException {
    public GitHubException(String message, Throwable e) {
        super(message, e);
    }
}
