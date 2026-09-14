package com.juliashtal.devanalytics.auth.service;

import com.juliashtal.devanalytics.auth.repository.PasswordResetTokenRepository;
import com.juliashtal.devanalytics.auth.repository.RefreshTokenRepository;
import com.juliashtal.devanalytics.config.SystemClock;
import com.juliashtal.devanalytics.invite.InviteTokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The cut-off every repository deletes against is one instant taken from the system clock, so the
 * deletes cannot disagree and the boundary is assertable.
 */
@ExtendWith(MockitoExtension.class)
class TokenCleanupSchedulerTest {

    private static final Instant FIXED = Instant.parse("2026-03-15T02:00:00Z");

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock InviteTokenRepository inviteTokenRepository;

    private final TimeZone originalZone = TimeZone.getDefault();

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    private TokenCleanupScheduler scheduler() {
        return new TokenCleanupScheduler(refreshTokenRepository, passwordResetTokenRepository,
                inviteTokenRepository, new SystemClock(Clock.fixed(FIXED, ZoneOffset.UTC)));
    }

    @Test
    void cleanupExpiredTokens_everyRepository_deletesAgainstTheSameCutOff() {
        scheduler().cleanupExpiredTokens();

        verify(refreshTokenRepository).deleteExpiredTokens(FIXED);
        verify(passwordResetTokenRepository).deleteExpiredOrUsedTokens(FIXED);
        verify(inviteTokenRepository).deleteExpiredOrRedeemed(FIXED);
    }

    @Test
    void cleanupExpiredTokens_serverZoneEastOrWestOfUtc_usesTheSameCutOff() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
        scheduler().cleanupExpiredTokens();

        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        scheduler().cleanupExpiredTokens();

        verify(refreshTokenRepository, times(2)).deleteExpiredTokens(FIXED);
        verify(passwordResetTokenRepository, times(2)).deleteExpiredOrUsedTokens(FIXED);
        verify(inviteTokenRepository, times(2)).deleteExpiredOrRedeemed(FIXED);
    }
}
