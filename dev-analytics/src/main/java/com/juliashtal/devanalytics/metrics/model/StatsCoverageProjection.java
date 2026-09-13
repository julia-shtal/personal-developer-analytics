package com.juliashtal.devanalytics.metrics.model;

import com.juliashtal.devanalytics.git.model.StatsSkipReason;
import com.juliashtal.devanalytics.git.model.StatsStatus;

/** Projection for stats-enrichment coverage: (status, skipReason, recordCount). */
public interface StatsCoverageProjection {
    StatsStatus getStatsStatus();
    StatsSkipReason getStatsSkipReason();
    long getRecordCount();
}
