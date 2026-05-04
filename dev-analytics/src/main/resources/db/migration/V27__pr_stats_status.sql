-- Two-phase PR ingestion: track enrichment state of per-PR size stats.
-- GitHub's PR list endpoint omits additions/deletions/changed_files/commits;
-- those require the single-PR detail endpoint.
-- Existing rows already have stats populated (or are acceptably 0), so they default to COMPLETE.

ALTER TABLE github_pull_requests
    ADD COLUMN stats_status    VARCHAR(20) NOT NULL DEFAULT 'COMPLETE',
    ADD COLUMN stats_fetched_at TIMESTAMP,
    ADD COLUMN stats_attempts  INT         NOT NULL DEFAULT 0;

-- Index to efficiently pick the next batch of PRs needing enrichment.
CREATE INDEX idx_github_prs_stats_status ON github_pull_requests (stats_status, created_at DESC)
    WHERE stats_status = 'PENDING';
