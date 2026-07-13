package com.juliashtal.devanalytics.auth.model.request;

import lombok.Data;

/**
 * Request carrying a refresh token to mint a new access token.
 */
@Data
public class TokenRefreshRequest {
    private String refreshToken;
}
