package com.juliashtal.devanalytics.metrics.model;

import java.sql.Date;

/** Shared projection for any daily-count aggregation: (day, repoId, count). */
public interface DailyCountProjection {
    Date getDay();
    Long getRepoId();
    Long getCount();
}
