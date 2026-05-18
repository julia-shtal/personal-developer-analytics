-- T1.4: Enforce type-specific column requirements at the DB level (ADR-001 pragmatic option).
-- DataSourceValidator provides pre-DB fast-fail with friendly messages;
-- these constraints are the enforcement backstop.

ALTER TABLE data_source_configs
    ADD CONSTRAINT chk_gitlocal_path
    CHECK (type != 'GIT_LOCAL' OR path IS NOT NULL);

ALTER TABLE data_source_configs
    ADD CONSTRAINT chk_remote_baseurl
    CHECK (type = 'GIT_LOCAL' OR base_url IS NOT NULL);

-- project_key was already drained to jira_projects in V32; kept as IF EXISTS for safety.
ALTER TABLE data_source_configs DROP COLUMN IF EXISTS project_key;

-- rollback:
--   ALTER TABLE data_source_configs DROP CONSTRAINT IF EXISTS chk_gitlocal_path;
--   ALTER TABLE data_source_configs DROP CONSTRAINT IF EXISTS chk_remote_baseurl;
