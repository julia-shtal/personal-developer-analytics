package com.juliashtal.devanalytics.metrics.model;

import com.juliashtal.devanalytics.git.model.StatsStatus;
import java.time.Instant;

/** Projection for per-commit details used by after-hours and refactor ratio: (authorDate, additions, deletions, statsStatus). */
public interface CommitDetailProjection {
    Instant     getAuthorDate();
    Integer     getAdditions();
    Integer     getDeletions();
    StatsStatus getStatsStatus();
}
