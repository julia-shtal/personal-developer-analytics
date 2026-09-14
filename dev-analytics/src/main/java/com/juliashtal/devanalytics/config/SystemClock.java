package com.juliashtal.devanalytics.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The injectable time source for scheduled work and for dates derived from "now".
 *
 * <p>The no-argument calendar methods read UTC, so the same input history yields the same figures
 * on every installation. Anything that assigns activity to a calendar day passes the user's zone
 * to {@link #today(ZoneId)} instead — {@code UserZone} resolves it.
 * Canonical definition: {@code docs/metrics/timezone.md}.</p>
 */
@Component
public class SystemClock {

    private final Clock clock;

    @Autowired
    public SystemClock() {
        this(Clock.system(ZoneOffset.UTC));
    }

    /** Test seam: a fixed clock makes derived ranges assertable rather than assumed. */
    public SystemClock(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** The current day in an explicitly chosen zone, for dates attributed to a user's calendar. */
    public LocalDate today(ZoneId zone) {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    public LocalDate yesterday() {
        return today().minusDays(1);
    }

    public Instant now() {
        return clock.instant();
    }
}
