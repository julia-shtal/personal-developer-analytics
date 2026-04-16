package com.juliashtal.devanalytics.exception;

public class JiraException extends RuntimeException {
    public JiraException(String message, Throwable e) {
        super(message, e);
    }
    public JiraException(String message) {
        super(message);
    }
}
