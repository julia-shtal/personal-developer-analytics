-- git_commits.hash was UNIQUE across the whole table, so a repository that shares history
-- with one already attached -- a fork, a mirror, a second clone -- collected nothing:
-- every hash it offered was taken. A commit identifies a revision within a repository, and
-- that is the scope the collectors already assume when they load existing hashes per repo.
--
-- No backfill: the old constraint made cross-repository duplicates impossible, so there is
-- nothing to reconcile before the narrower one is enforced.

ALTER TABLE git_commits DROP CONSTRAINT IF EXISTS git_commits_hash_key;

ALTER TABLE git_commits
    ADD CONSTRAINT uk_git_commits_repo_hash UNIQUE (repository_id, hash);

-- rollback:
-- ALTER TABLE git_commits DROP CONSTRAINT IF EXISTS uk_git_commits_repo_hash;
-- ALTER TABLE git_commits ADD CONSTRAINT git_commits_hash_key UNIQUE (hash);
