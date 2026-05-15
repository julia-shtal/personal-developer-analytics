package com.juliashtal.devanalytics.user.model;

import lombok.Data;

@Data
public class UserSummary {
    private Long id;
    private String username;
    private String email;
    private Role role;
    private String timezone;
    private String githubLogin;
    private boolean hasCustomAvatar;
    private String avatarPreset;

    public static UserSummary from(User user) {
        UserSummary dto = new UserSummary();
        dto.id = user.getId();
        dto.username = user.getUsername();
        dto.email = user.getEmail();
        dto.role = user.getRole();
        dto.timezone = user.getTimezone();
        dto.githubLogin = user.getGithubLogin();
        dto.hasCustomAvatar = user.getAvatarData() != null;
        dto.avatarPreset = user.getAvatarPreset();
        return dto;
    }
}
