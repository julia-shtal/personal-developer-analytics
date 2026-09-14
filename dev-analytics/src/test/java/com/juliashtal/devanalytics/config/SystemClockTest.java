package com.juliashtal.devanalytics.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the clock to UTC: the same instant must resolve to the same calendar day whatever zone the
 * JVM was started in, which is the property every scheduled job derives its range from.
 */
class SystemClockTest {

    /** Auckland is already on the 15th at this instant; Los Angeles is still on the 14th. */
    private static final Instant FIXED = Instant.parse("2026-03-15T00:30:00Z");

    private final TimeZone originalZone = TimeZone.getDefault();

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    @Test
    void today_fixedInstant_isTheUtcDay() {
        SystemClock clock = new SystemClock(Clock.fixed(FIXED, ZoneOffset.UTC));

        assertThat(clock.today()).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    void yesterday_fixedInstant_isTheDayBeforeTheUtcDay() {
        SystemClock clock = new SystemClock(Clock.fixed(FIXED, ZoneOffset.UTC));

        assertThat(clock.yesterday()).isEqualTo(LocalDate.of(2026, 3, 14));
    }

    /**
     * The zone-taking overload is what callers attributing activity to a user's calendar day use;
     * at this instant it must disagree with the UTC reading rather than track it.
     */
    @Test
    void today_explicitZone_readsThatZoneNotUtc() {
        SystemClock clock = new SystemClock(Clock.fixed(FIXED, ZoneOffset.UTC));

        assertThat(clock.today(ZoneId.of("Pacific/Auckland"))).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(clock.today(ZoneId.of("America/Los_Angeles"))).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(clock.today()).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    void now_fixedInstant_returnsThatInstant() {
        SystemClock clock = new SystemClock(Clock.fixed(FIXED, ZoneOffset.UTC));

        assertThat(clock.now()).isEqualTo(FIXED);
    }

    @Test
    void today_defaultConstructor_serverZoneEastOrWestOfUtc_agreeOnTheDay() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
        LocalDate east = new SystemClock().today();

        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        LocalDate west = new SystemClock().today();

        assertThat(east).isEqualTo(west).isEqualTo(LocalDate.now(ZoneOffset.UTC));
    }
}
