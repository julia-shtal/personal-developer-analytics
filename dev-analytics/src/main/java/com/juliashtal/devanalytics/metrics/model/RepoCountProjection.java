package com.juliashtal.devanalytics.metrics.model;

/** Projection for per-repo commit counts: (repoId, count). */
public interface RepoCountProjection {
    Long getRepoId();
    Long getCount();
}
