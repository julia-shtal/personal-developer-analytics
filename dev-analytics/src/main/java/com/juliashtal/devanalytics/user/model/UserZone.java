package com.juliashtal.devanalytics.user.model;

import lombok.extern.slf4j.Slf4j;

import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Resolves the zone a user's activity is attributed to, the single reading of {@code User.timezone}
 * shared by the calculators, the backfill window, and the backfill request guard.
 *
 * <p>This is the attribution clock's zone. System-scheduled work reads
 * {@code SystemClock} instead, which is fixed to UTC. Canonical definition:
 * {@code docs/metrics/timezone.md}.</p>
 */
@Slf4j
public final class UserZone {

    private UserZone() {
    }

    /** An unset or unparseable zone falls back to UTC rather than failing the caller. */
    public static ZoneId of(User user) {
        String timezone = user.getTimezone();
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException e) {
            log.warn("Unparseable timezone '{}' for userId={}, falling back to UTC",
                    timezone, user.getId());
            return ZoneOffset.UTC;
        }
    }
}
