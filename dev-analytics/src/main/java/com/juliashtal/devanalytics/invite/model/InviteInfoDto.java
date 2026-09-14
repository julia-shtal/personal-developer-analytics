package com.juliashtal.devanalytics.invite.model;

/**
 * Public invite details shown on the registration page.
 */
public record InviteInfoDto(
        String email,
        String role,
        String teamName
) {}
