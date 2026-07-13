package com.juliashtal.devanalytics.auth.model.request;

import lombok.Data;

/**
 * Request to start a password reset for an email address.
 */
@Data
public class PasswordResetRequest {
    private String email;
}
