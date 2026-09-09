package com.juliashtal.devanalytics.metrics.model;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the history backfill.
 *
 * @param maxDaysPerRun days of history one backfill run may compute for one user. The cap is a
 *                      resumable throttle rather than truncation — whatever it defers is still
 *                      missing on the next run — so raising it fills a long range in fewer
 *                      scheduled passes. Constrained to at least one: a zero or negative cap
 *                      would leave the feature silently dead, so it fails the context at
 *                      startup with a message naming the property instead.
 */
@Validated
@ConfigurationProperties("app.metrics.backfill")
public record BackfillProperties(@Min(1) int maxDaysPerRun) {
}
