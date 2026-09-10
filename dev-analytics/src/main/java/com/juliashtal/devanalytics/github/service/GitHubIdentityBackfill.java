package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;

/**
 * Fills the numeric identity columns on records collected before author attribution existed.
 *
 * <p>Needed because every ingest path is incremental and so never revisits an existing row,
 * leaving its ID columns null permanently. An interface so the migration job depends on the four
 * steps rather than on GitHub transport code; each is idempotent and safe to re-run.</p>
 */
public interface GitHubIdentityBackfill {

    /** Walks {@code /commits} and sets {@code author_github_id} / {@code author_github_login} by hash. */
    void backfillCommits(GitRepositoryEntity repo);

    /** Walks {@code /pulls?state=all} and sets {@code author_github_id} by {@code (repository_id, number)}. */
    void backfillPullRequests(GitRepositoryEntity repo);

    /** Re-fetches reviews per PR so the replacement rows carry {@code reviewer_github_id}. */
    void refreshReviews(GitRepositoryEntity repo);

    /** Re-collects issues so creator and assignee IDs are stored. */
    void backfillIssues(GitRepositoryEntity repo);
}
