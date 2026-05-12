ALTER TABLE git_repositories
    ADD COLUMN collect_issues        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN issues_last_synced_at TIMESTAMPTZ;
