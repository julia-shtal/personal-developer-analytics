package com.juliashtal.devanalytics.metrics.model;

import java.time.LocalDate;

/**
 * Outcome of one backfill pass over a user's coverage gap.
 *
 * @param daysComputed  days this run calculated; zero when nothing was missing
 * @param daysRemaining days still missing after this run — the number the per-run cap deferred
 *                      (plus any block that failed), and therefore what the next run picks up
 * @param coverageFrom  first day of the target range (earliest collected activity), or
 *                      {@code null} when there is no target range at all. Three distinct cases
 *                      collapse into that null: the user has no repository in scope, the scope
 *                      holds no collected commit, pull request or issue, or all collected
 *                      activity falls after the range's last day — history collected today,
 *                      which nothing is due to compute until tomorrow. Readers presenting this
 *                      to a user cannot tell "no data" from "nothing due yet" from this field
 *                      alone.
 * @param coverageTo    last day of the target range (yesterday in the user's zone), or
 *                      {@code null} in the same three cases
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
