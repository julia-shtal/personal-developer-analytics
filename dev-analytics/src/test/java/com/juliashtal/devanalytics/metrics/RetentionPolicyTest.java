package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.config.SystemClock;
import com.juliashtal.devanalytics.metrics.service.RetentionPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RetentionPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    private RetentionPolicy policy(int months) {
        return new RetentionPolicy(months, new SystemClock(Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    @Test
    void isEnabled_positiveMonths_true() {
        assertThat(policy(24).isEnabled()).isTrue();
    }

    @Test
    void isEnabled_zeroMonths_false() {
        assertThat(policy(0).isEnabled()).isFalse();
    }

    @Test
    void horizon_positiveMonths_isTodayMinusThatManyMonths() {
        assertThat(policy(24).horizon()).isEqualTo(TODAY.minusMonths(24));
    }

    @Test
    void horizon_zeroMonths_isLocalDateMin() {
        assertThat(policy(0).horizon()).isEqualTo(LocalDate.MIN);
    }
}
