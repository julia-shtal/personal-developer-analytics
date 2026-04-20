-- Add repo_full_name to git_repositories for GitHub repo deduplication.
-- For GitHub repos this is "owner/repo" (globally unique in the GitHub namespace).
-- NULL for GIT_LOCAL repos (they are deduplicated by (data_source_id, local_path) already).

ALTER TABLE git_repositories
    ADD COLUMN repo_full_name VARCHAR(255);

CREATE UNIQUE INDEX uk_git_repo_fullname
    ON git_repositories(repo_full_name)
    WHERE repo_full_name IS NOT NULL;
