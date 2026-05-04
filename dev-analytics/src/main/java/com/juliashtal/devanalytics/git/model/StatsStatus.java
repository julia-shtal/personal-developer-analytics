package com.juliashtal.devanalytics.git.model;

/**
 * Tracks the enrichment state of per-commit stats (additions/deletions/filesChanged).
 *
 * <p>GitHub's list endpoint omits stats; they require a separate per-commit detail call.
 * Commits are saved immediately as PENDING, then enriched asynchronously.</p>
 *
 * <ul>
 *   <li>PENDING  — saved from list endpoint, stats not yet fetched</li>
 *   <li>COMPLETE — stats successfully fetched from GitHub detail endpoint</li>
 *   <li>FAILED   — detail call failed after max attempts (stats remain 0)</li>
 *   <li>SKIPPED  — diff too large or commit excluded from enrichment window</li>
 * </ul>
 */
public enum StatsStatus {
    PENDING,
    COMPLETE,
    FAILED,
    SKIPPED
}
