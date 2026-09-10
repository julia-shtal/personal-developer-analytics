package com.juliashtal.devanalytics.metrics.model;

import java.time.LocalDate;

/**
 * Outcome of one backfill pass over a user's coverage gap.
 *
 * @param daysComputed  days this run calculated; zero when nothing was missing
 * @param daysRemaining days still missing after this run, and so what the next run picks up
 * @param coverageFrom  first day of the target range, or {@code null} when there is no range —
 *                      no repository in scope, nothing collected, or nothing due yet, which this
 *                      field alone cannot tell apart
 * @param coverageTo    last day of the target range (yesterday), or {@code null} likewise
 */
public record BackfillResult(
        long daysComputed,
        long daysRemaining,
        LocalDate coverageFrom,
        LocalDate coverageTo) {

    /** The result for a user with no repository scope or no collected activity: nothing to cover. */
    public static BackfillResult empty() {
        return new BackfillResult(0, 0, null, null);
    }
}
