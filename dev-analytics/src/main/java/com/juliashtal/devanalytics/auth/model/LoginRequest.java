package com.juliashtal.devanalytics.auth.model;

import lombok.Data;

@Data
public class LoginRequest {
    private String usernameOrEmail;
    private String password;
}
