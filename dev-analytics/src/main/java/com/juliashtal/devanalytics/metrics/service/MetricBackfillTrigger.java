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
     * coverage — days computed before this repository existed were computed without it — and
     * runs a bounded backfill pass immediately, so a newly attached repository does not wait
     * for the nightly catch-up job.
     *
     * <p>The two steps are sequential, not atomic. The reset commits before any metric is
     * recomputed, so a failure in the backfill leaves the user with their coverage cleared and
     * not yet rebuilt. That state is recoverable — later runs refill it a capped batch at a
     * time — but it is not a rollback, and implementations are expected to log the failure
     * rather than hide it.
     *
     * <p>CPU- and database-bound over up to a month of history: callers must invoke this off
     * the request thread.
     */
    void onFirstCollection(Long userId);
}
