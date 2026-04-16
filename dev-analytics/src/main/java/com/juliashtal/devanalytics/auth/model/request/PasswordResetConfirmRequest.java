package com.juliashtal.devanalytics.auth.model.request;

import lombok.Data;

@Data
public class PasswordResetConfirmRequest {
    private String token;
    private String newPassword;
}
