package com.juliashtal.devanalytics.invite;

import java.time.Instant;

public record InviteTokenDto(
        String token,
        String email,
        String role,
        Instant expiresAt,
        String inviteUrl
) {}
