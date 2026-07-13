package com.juliashtal.devanalytics.auth.model.request;

import lombok.Data;

/**
 * Login request (username or email plus password).
 */
@Data
public class LoginRequest {
    private String usernameOrEmail;
    private String password;
}
