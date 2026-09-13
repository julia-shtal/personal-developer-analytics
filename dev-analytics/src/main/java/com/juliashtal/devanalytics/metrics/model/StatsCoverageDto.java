package com.juliashtal.devanalytics.metrics.model;

import com.juliashtal.devanalytics.git.model.StatsSkipReason;
import com.juliashtal.devanalytics.git.model.StatsStatus;

/**
 * One enrichment state within a requested window: how many records reached it and what share of
 * that record type they are.
 *
 * <p>{@code share} is computed within a record type, so commit shares and pull-request shares
 * each sum to 1 rather than to 1 between them.</p>
 */
public record StatsCoverageDto(
        StatsCoverageRecordType recordType,
        StatsStatus statsStatus,
        StatsSkipReason statsSkipReason,
        long recordCount,
        double share
) {
}
