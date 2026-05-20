-- Add an explicit repo_type discriminator column to git_repositories so that
-- the LOCAL vs GITHUB polymorphism is type-safe rather than implied by
-- which nullable columns are populated (see ADR-003).

ALTER TABLE git_repositories ADD COLUMN repo_type VARCHAR(16);

-- Backfill: a row is LOCAL when it has a local_path, GITHUB when it has a repo_full_name.
UPDATE git_repositories SET repo_type = 'LOCAL'  WHERE local_path     IS NOT NULL;
UPDATE git_repositories SET repo_type = 'GITHUB' WHERE repo_full_name IS NOT NULL;

ALTER TABLE git_repositories ALTER COLUMN repo_type SET NOT NULL;

ALTER TABLE git_repositories
    ADD CONSTRAINT chk_repo_local_path
    CHECK (repo_type != 'LOCAL' OR local_path IS NOT NULL);

ALTER TABLE git_repositories
    ADD CONSTRAINT chk_repo_github_fullname
    CHECK (repo_type != 'GITHUB' OR repo_full_name IS NOT NULL);

-- rollback:
-- ALTER TABLE git_repositories DROP CONSTRAINT IF EXISTS chk_repo_local_path;
-- ALTER TABLE git_repositories DROP CONSTRAINT IF EXISTS chk_repo_github_fullname;
-- ALTER TABLE git_repositories DROP COLUMN IF EXISTS repo_type;
