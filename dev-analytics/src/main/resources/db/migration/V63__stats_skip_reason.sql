-- SKIPPED was set for two unrelated causes, so the line-count metrics' exclusion set could not
-- be characterised: an oversized diff is evidence about the repository's commit-size
-- distribution, a missing detail record is evidence about API access.
--
-- A nullable reason column rather than new StatsStatus values: every query, projection and
-- metric filter that tests `= 'SKIPPED'` keeps working, and the status enum stays a four-state
-- lifecycle instead of growing one variant per cause.

ALTER TABLE git_commits          ADD COLUMN stats_skip_reason VARCHAR(32);
ALTER TABLE github_pull_requests ADD COLUMN stats_skip_reason VARCHAR(32);

-- Rows skipped before this migration carry no record of which cause applied. UNKNOWN keeps them
-- countable without attributing them to either one.
UPDATE git_commits          SET stats_skip_reason = 'UNKNOWN'
    WHERE stats_status = 'SKIPPED' AND stats_skip_reason IS NULL;
UPDATE github_pull_requests SET stats_skip_reason = 'UNKNOWN'
    WHERE stats_status = 'SKIPPED' AND stats_skip_reason IS NULL;

-- Serves a direct breakdown of the skipped rows themselves
-- (WHERE stats_status = 'SKIPPED' ... GROUP BY stats_skip_reason) without indexing the COMPLETE
-- majority. The endpoint's own GROUP BY spans every status and cannot use a partial index.
CREATE INDEX ix_git_commits_skip_reason
    ON git_commits (repository_id, stats_skip_reason)
    WHERE stats_status = 'SKIPPED';

CREATE INDEX ix_github_prs_skip_reason
    ON github_pull_requests (repository_id, stats_skip_reason)
    WHERE stats_status = 'SKIPPED';
