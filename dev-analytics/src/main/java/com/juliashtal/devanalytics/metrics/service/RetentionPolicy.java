package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.config.SystemClock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * The single source of truth for how long computed metrics data is kept.
 * <p>{@code metricsMonths <= 0} disables retention entirely: nothing is deleted and every read
 * clamp becomes a no-op, since {@link #horizon()} then returns {@link LocalDate#MIN}.</p>
 */
@Component
public class RetentionPolicy {

    private final int metricsMonths;
    private final SystemClock systemClock;

    public RetentionPolicy(@Value("${app.retention.metrics-months:24}") int metricsMonths,
                            SystemClock systemClock) {
        this.metricsMonths = metricsMonths;
        this.systemClock = systemClock;
    }

    public boolean isEnabled() {
        return metricsMonths > 0;
    }

    /** Earliest date still inside the retention window; {@link LocalDate#MIN} when disabled. */
    public LocalDate horizon() {
        return isEnabled() ? systemClock.today().minusMonths(metricsMonths) : LocalDate.MIN;
    }
}
