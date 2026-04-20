package com.juliashtal.devanalytics.user.model.request;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String username;
    private String timezone;
    private String githubLogin;
}