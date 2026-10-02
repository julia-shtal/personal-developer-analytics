-- V74: default branch per repository — GitHub's API `default_branch` for GitHub repos, the
-- branch HEAD points to for local repos. Needed to tell a merge into the mainline from a merge
-- into a feature/release branch (MERGES_TO_DEFAULT_BRANCH_PER_WEEK).
-- Nullable: GitHub's value requires an API call, which a SQL migration cannot make, so existing
-- rows are backfilled lazily the next time each repository is synced, not by this migration.
ALTER TABLE git_repositories ADD COLUMN default_branch VARCHAR(255);
