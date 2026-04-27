-- V22: Add ON DELETE CASCADE to all FKs in the DataSource → repo → data chain
-- so that deleting a DataSourceConfig removes all derived data automatically.

-- 1. git_repositories → data_source_configs
ALTER TABLE git_repositories
    DROP CONSTRAINT fk_git_repositories_data_source,
    ADD CONSTRAINT fk_git_repositories_data_source
        FOREIGN KEY (data_source_id) REFERENCES data_source_configs(id) ON DELETE CASCADE;

-- 2. git_commits → git_repositories
ALTER TABLE git_commits
    DROP CONSTRAINT fk_git_commits_repository,
    ADD CONSTRAINT fk_git_commits_repository
        FOREIGN KEY (repository_id) REFERENCES git_repositories(id) ON DELETE CASCADE;

-- 3. github_pull_requests → git_repositories
ALTER TABLE github_pull_requests
    DROP CONSTRAINT fk_github_pr_repository,
    ADD CONSTRAINT fk_github_pr_repository
        FOREIGN KEY (repository_id) REFERENCES git_repositories(id) ON DELETE CASCADE;

-- 4. metric_snapshots → git_repositories (nullable – cascade removes per-repo snapshots)
ALTER TABLE metric_snapshots
    DROP CONSTRAINT fk_metric_snapshots_repository,
    ADD CONSTRAINT fk_metric_snapshots_repository
        FOREIGN KEY (repository_id) REFERENCES git_repositories(id) ON DELETE CASCADE;

-- 5. issues.data_source_id → data_source_configs
ALTER TABLE issues
    DROP CONSTRAINT fk_issues_data_source,
    ADD CONSTRAINT fk_issues_data_source
        FOREIGN KEY (data_source_id) REFERENCES data_source_configs(id) ON DELETE CASCADE;

-- 6. issues.repository_id → git_repositories (nullable)
ALTER TABLE issues
    DROP CONSTRAINT IF EXISTS fk_issues_repository,
    ADD CONSTRAINT fk_issues_repository
        FOREIGN KEY (repository_id) REFERENCES git_repositories(id) ON DELETE CASCADE;
