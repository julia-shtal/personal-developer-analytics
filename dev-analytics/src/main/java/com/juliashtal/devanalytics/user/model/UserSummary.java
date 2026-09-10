package com.juliashtal.devanalytics.user.model;

import lombok.Data;

/**
 * Lightweight user projection for listings and team membership.
 */
@Data
public class UserSummary {
    private Long id;
    private String username;
    private String email;
    private Role role;
    private String timezone;
    private String githubLogin;
    /** True once the login has been resolved to a numeric GitHub account ID. */
    private boolean githubLoginVerified;
    private boolean hasCustomAvatar;
    private String avatarPreset;
    private String jiraAccountId;

    public static UserSummary from(User user) {
        UserSummary dto = new UserSummary();
        dto.id = user.getId();
        dto.username = user.getUsername();
        dto.email = user.getEmail();
        dto.role = user.getRole();
        dto.timezone = user.getTimezone();
        dto.githubLogin = user.getGithubLogin();
        dto.githubLoginVerified = user.getGithubUserId() != null;
        dto.jiraAccountId = user.getJiraAccountId();
        dto.hasCustomAvatar = user.getAvatarData() != null;
        dto.avatarPreset = user.getAvatarPreset();
        return dto;
    }
}
