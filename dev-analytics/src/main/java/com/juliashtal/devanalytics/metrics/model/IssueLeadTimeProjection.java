package com.juliashtal.devanalytics.metrics.model;

import java.time.Instant;

/** Projection for issue lead times: (repoId, createdAt, closedAt). */
public interface IssueLeadTimeProjection {
    Long    getRepoId();
    Instant getCreatedAt();
    Instant getClosedAt();
}
