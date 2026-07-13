package com.juliashtal.devanalytics.exception;

/**
 * Thrown when a Jira API call or ingestion fails.
 */
public class JiraException extends RuntimeException {
    public JiraException(String message, Throwable e) {
        super(message, e);
    }
    public JiraException(String message) {
        super(message);
    }
}
