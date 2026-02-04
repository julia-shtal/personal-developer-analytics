package com.juliashtal.devanalytics.exception;

public class GitException extends RuntimeException {
    public GitException(String message, Exception e) {
        super(message);
    }
}
