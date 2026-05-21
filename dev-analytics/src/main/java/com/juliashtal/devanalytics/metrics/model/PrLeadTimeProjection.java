package com.juliashtal.devanalytics.metrics.model;

import java.time.Instant;

/** Projection for merged-PR lead times: (repoId, createdAt, mergedAt). */
public interface PrLeadTimeProjection {
    Long    getRepoId();
    Instant getCreatedAt();
    Instant getMergedAt();
}
