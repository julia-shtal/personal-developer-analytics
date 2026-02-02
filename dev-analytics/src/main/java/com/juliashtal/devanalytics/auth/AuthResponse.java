package com.juliashtal.devanalytics.auth;

import lombok.Data;

@Data
public class AuthResponse {
    private String accessToken;
    private String tokenType = "Bearer";

    public AuthResponse() {}
    public AuthResponse(String accessToken) {
        this.accessToken = accessToken;
    }
}
