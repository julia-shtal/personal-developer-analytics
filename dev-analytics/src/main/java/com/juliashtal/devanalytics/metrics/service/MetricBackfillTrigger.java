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
     * coverage and runs a bounded backfill, so a new repository need not wait for the nightly job.
     *
     * <p>Reset and recomputation are sequential, not atomic, so a failure leaves coverage cleared
     * but not rebuilt. Database-bound: callers must invoke this off the request thread.</p>
     */
    void onFirstCollection(Long userId);

    /**
     * Called when a user's attribution identity changed — an address, GitHub account or Jira
     * accountId added, removed or relinked.
     *
     * <p>Deletes every snapshot and coverage row for that user, then recomputes: calculators
     * upsert and never delete, so rows the old identity produced would otherwise survive.
     * Team-scoped rows are deleted but rebuilt only by the next team calculation. Same
     * non-atomic, off-the-request-thread contract as {@link #onFirstCollection}.</p>
     */
    void onAttributionChanged(Long userId);
}
