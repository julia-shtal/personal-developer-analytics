package com.juliashtal.devanalytics.auth.model.request;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    // optional — required for PR attribution in team-scoped GitHub repos
    private String githubLogin;
    // optional — invite token from /register?invite=... URL
    private String inviteToken;
}
