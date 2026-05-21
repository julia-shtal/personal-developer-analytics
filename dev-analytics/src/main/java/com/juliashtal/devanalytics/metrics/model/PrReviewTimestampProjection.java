package com.juliashtal.devanalytics.metrics.model;

import java.time.Instant;

/** Projection for first PR review timestamps: (prId, reviewedAt). */
public interface PrReviewTimestampProjection {
    Long    getPrId();
    Instant getReviewedAt();
}
