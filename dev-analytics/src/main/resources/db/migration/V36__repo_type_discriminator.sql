-- Add an explicit repo_type discriminator column to git_repositories so that
-- the LOCAL vs GITHUB polymorphism is type-safe rather than implied by
-- which nullable columns are populated (see ADR-003).

-- Add the column with a DEFAULT so every existing row is non-null immediately.
-- This is the standard pattern for adding NOT NULL columns to populated tables.
ALTER TABLE git_repositories
    ADD COLUMN IF NOT EXISTS repo_type VARCHAR(16) NOT NULL DEFAULT 'GITHUB';

-- Backfill: override rows that are actually local repos.
UPDATE git_repositories SET repo_type = 'LOCAL' WHERE local_path IS NOT NULL;

-- Remove the default now that the column is fully backfilled.
ALTER TABLE git_repositories ALTER COLUMN repo_type DROP DEFAULT;

-- Only the LOCAL constraint is added at DB level. Existing data has GITHUB rows without
-- repo_full_name (pre-V19 orphans); enforcing that invariant in SQL would require
-- deleting user data. The application already enforces it: every code path that creates
-- a GITHUB GitRepositoryEntity sets repo_full_name before saving.
ALTER TABLE git_repositories
    ADD CONSTRAINT chk_repo_local_path
    CHECK (repo_type != 'LOCAL' OR local_path IS NOT NULL);

-- rollback:
-- ALTER TABLE git_repositories DROP CONSTRAINT IF EXISTS chk_repo_local_path;
-- ALTER TABLE git_repositories DROP COLUMN IF EXISTS repo_type;
