package com.juliashtal.devanalytics.auth.model.request;

import lombok.Data;

/**
 * Request to complete a password reset with a token and new password.
 */
@Data
public class PasswordResetConfirmRequest {
    private String token;
    private String newPassword;
}
