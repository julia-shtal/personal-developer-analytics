-- T1.2: Eliminate GITHUB_ISSUES DataSourceType.
-- GitHub issue collection is now driven exclusively by git_repositories.collect_issues.

-- Step 1: Enable issue collection on every repo that belonged to a GITHUB_ISSUES datasource.
UPDATE git_repositories
SET    collect_issues = true
WHERE  data_source_id IN (
    SELECT id FROM data_source_configs WHERE type = 'GITHUB_ISSUES'
);

-- Step 2: Convert all GITHUB_ISSUES datasources to GITHUB.
-- Credentials, repos, and issue rows remain intact; only the type label changes.
UPDATE data_source_configs
SET    type = 'GITHUB'
WHERE  type = 'GITHUB_ISSUES';

-- Verify no orphaned issues (data_source_id FKs still valid after the type change above).
-- No rows should exist with type 'GITHUB_ISSUES' after this point.

-- rollback:
--   This migration cannot be cleanly reversed without a backup of the original type values.
--   To roll forward: restore DB from snapshot and redeploy the prior version.
