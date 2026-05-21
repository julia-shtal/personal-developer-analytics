package com.juliashtal.devanalytics.metrics.model;

import java.sql.Date;

/** Projection for daily commit aggregations: (day, repoId, commitsCount, avgSize). */
public interface DailyCommitsProjection {
    Date   getDay();
    Long   getRepoId();
    Long   getCommitsCount();
    Double getAvgSize();    // nullable — null when no commits match
}
