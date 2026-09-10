package com.juliashtal.devanalytics.metrics.model;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the history backfill.
 *
 * @param maxDaysPerRun days of history one backfill run may compute for one user; a resumable
 *                      throttle, since whatever it defers is still missing on the next run.
 *                      Constrained to at least one, so a dead cap fails startup rather than
 *                      silently disabling the feature.
 */
@Validated
@ConfigurationProperties("app.metrics.backfill")
public record BackfillProperties(@Min(1) int maxDaysPerRun) {
}
