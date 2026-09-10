package com.juliashtal.devanalytics.metrics.service;

/**
 * The one write the {@code datasource} package may make into the metrics package.
 *
 * <p>Cross-package service calls go through an interface rather than a concrete service, so
 * collection orchestration depends on the contract "a first collection landed for this user"
 * and not on how the backfill decides what to compute.
 */
public interface MetricBackfillTrigger {

    /**
     * Called once a data source completes its first successful collection. Clears the user's
     * coverage (since prior days were computed without this repository) and immediately runs
     * a bounded backfill, so a newly attached repository doesn't wait for the nightly job.
     *
     * <p>The two steps are sequential, not atomic: the reset commits before recomputation
     * starts, so a backfill failure leaves coverage cleared but not rebuilt. This is
     * recoverable — later runs refill it in capped batches — but not a rollback, and failures
     * should be logged, not swallowed.
     *
     * <p>CPU- and database-bound over up to a month of history: callers must invoke this off
     * the request thread.
     */
    void onFirstCollection(Long userId);
}
