package com.juliashtal.devanalytics.invite;

import java.time.Instant;

/**
 * Generated invite token with its shareable URL.
 */
public record InviteTokenDto(
        String token,
        String email,
        String role,
        Instant expiresAt,
        String inviteUrl
) {}
