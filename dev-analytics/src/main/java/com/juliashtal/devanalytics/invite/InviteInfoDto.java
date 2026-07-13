package com.juliashtal.devanalytics.invite;

/**
 * Public invite details shown on the registration page.
 */
public record InviteInfoDto(
        String email,
        String role,
        String teamName
) {}
