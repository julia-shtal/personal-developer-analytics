package com.juliashtal.devanalytics.exception;

/**
 * Thrown when a GitHub API call or ingestion fails.
 */
public class GitHubException extends RuntimeException {
    public GitHubException(String message, Throwable e) {
        super(message, e);
    }
    public GitHubException(String message) {
        super(message);
    }
}
