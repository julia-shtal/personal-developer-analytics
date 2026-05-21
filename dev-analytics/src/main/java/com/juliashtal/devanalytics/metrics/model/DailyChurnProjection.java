package com.juliashtal.devanalytics.metrics.model;

import java.sql.Date;

/** Projection for daily churn aggregations: (day, repoId, additions, deletions). */
public interface DailyChurnProjection {
    Date getDay();
    Long getRepoId();
    Long getAdditions();
    Long getDeletions();
}
