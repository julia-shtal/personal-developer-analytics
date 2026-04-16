package com.juliashtal.devanalytics.auth.model.request;

import lombok.Data;

@Data
public class TokenRefreshRequest {
    private String refreshToken;
}
