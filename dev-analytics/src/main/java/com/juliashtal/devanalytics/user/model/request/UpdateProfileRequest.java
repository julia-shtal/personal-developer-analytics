package com.juliashtal.devanalytics.user.model.request;

import lombok.Data;

/**
 * Request to update the current user's profile.
 */
@Data
public class UpdateProfileRequest {
    private String username;
    private String email;
    private String timezone;
    private String githubLogin;
    private String jiraAccountId;
}