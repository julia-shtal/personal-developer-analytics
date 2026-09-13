package com.juliashtal.devanalytics.git.model;

/**
 * Why a record's stats enrichment ended in {@link StatsStatus#SKIPPED}.
 *
 * <p>Null on every record that is not SKIPPED. The two live causes bear on metric validity in
 * opposite directions, so the line-count metrics report them separately rather than as one
 * exclusion count.</p>
 *
 * <ul>
 *   <li>DIFF_TOO_LARGE     — GitHub declined to render the diff, reporting a size limit</li>
 *   <li>RECORD_UNAVAILABLE — the detail endpoint answered 404 or 422 for the record</li>
 *   <li>UNKNOWN            — skipped before the causes were distinguished</li>
 * </ul>
 */
public enum StatsSkipReason {
    DIFF_TOO_LARGE,
    RECORD_UNAVAILABLE,
    UNKNOWN
}
