-- Index on repository_id for FK lookups and hash scans on git_commits.
-- PostgreSQL does not automatically create an index for FK columns.
CREATE INDEX IF NOT EXISTS ix_git_commits_repository_id
    ON git_commits (repository_id);

-- Composite index for the daily commit aggregation query
-- (aggregateCommitsDailyByRepoIdsAndAuthorEmail).
CREATE INDEX IF NOT EXISTS ix_git_commits_repo_email_date
    ON git_commits (repository_id, author_email, author_date);

-- Index on repository_id for FK lookups on github_pull_requests.
CREATE INDEX IF NOT EXISTS ix_github_pr_repository_id
    ON github_pull_requests (repository_id);

-- Composite index for PR aggregation queries filtered by repo + author + date.
CREATE INDEX IF NOT EXISTS ix_github_pr_repo_login_created
    ON github_pull_requests (repository_id, author_login, created_at);

CREATE INDEX IF NOT EXISTS ix_github_pr_repo_login_merged
    ON github_pull_requests (repository_id, author_login, merged_at);

-- Index for the review response time query (JOIN on pr_id + ordering by submitted_at).
CREATE INDEX IF NOT EXISTS ix_github_pr_reviews_pr_submitted
    ON github_pr_reviews (pr_id, submitted_at);
