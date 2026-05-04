-- Two-phase commit ingestion: track enrichment state of per-commit stats.
-- Existing rows already have stats populated, so they default to COMPLETE.
-- New GitHub commits will be inserted as PENDING and enriched asynchronously.

ALTER TABLE git_commits
    ADD COLUMN stats_status   VARCHAR(20)  NOT NULL DEFAULT 'COMPLETE',
    ADD COLUMN stats_fetched_at TIMESTAMP,
    ADD COLUMN stats_attempts INT          NOT NULL DEFAULT 0;

-- Index to efficiently pick the next batch of commits needing enrichment.
CREATE INDEX idx_git_commits_stats_status ON git_commits (stats_status, author_date DESC)
    WHERE stats_status = 'PENDING';
