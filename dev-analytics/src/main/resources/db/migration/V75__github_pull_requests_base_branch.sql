-- V75: the pull request's target ("base") branch, captured from GitHub's PR list endpoint
-- (`base.ref`) at ingest time — no extra API call, the field is already in the list response.
-- Nullable: PRs ingested before this migration keep base_branch = NULL until their repository
-- is next synced AND that PR's updated_at changes (unchanged PRs are skipped by incremental
-- sync and never re-mapped). Such PRs are excluded from MERGES_TO_DEFAULT_BRANCH_PER_WEEK
-- rather than assumed to target the default branch.
ALTER TABLE github_pull_requests ADD COLUMN base_branch VARCHAR(255);
