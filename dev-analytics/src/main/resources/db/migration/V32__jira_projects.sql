-- Introduce jira_projects as the subscribable intermediary for Jira, mirroring git_repositories.
-- Issues for Jira are re-parented from data_source_configs to jira_projects.

CREATE TABLE jira_projects (
    id             BIGSERIAL PRIMARY KEY,
    data_source_id BIGINT NOT NULL REFERENCES data_source_configs (id) ON DELETE CASCADE,
    project_key    VARCHAR(32) NOT NULL,
    project_name   VARCHAR(255),
    last_scan_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_jira_project_ds_key UNIQUE (data_source_id, project_key)
);

-- Backfill: one jira_projects row per existing JIRA datasource that had a project_key.
INSERT INTO jira_projects (data_source_id, project_key)
SELECT id, project_key
FROM   data_source_configs
WHERE  type = 'JIRA'
  AND  project_key IS NOT NULL;

-- Subscription table mirroring user_repo_registrations.
CREATE TABLE user_project_registrations (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES jira_projects (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_project_reg UNIQUE (user_id, project_id)
);

-- Add jira_project_id to issues; data_source_id becomes optional (GitHub issues keep it).
ALTER TABLE issues
    ADD COLUMN jira_project_id BIGINT REFERENCES jira_projects (id) ON DELETE CASCADE;

-- data_source_id becomes optional before we null it out for Jira issues.
ALTER TABLE issues ALTER COLUMN data_source_id DROP NOT NULL;

-- Backfill jira_project_id for existing Jira issues.
-- Jira issues have no repository_id (only GitHub issues do).
UPDATE issues i
SET    jira_project_id = jp.id
FROM   jira_projects jp
WHERE  i.data_source_id = jp.data_source_id
  AND  i.repository_id IS NULL;

-- Detach migrated Jira issues from data_source_configs.
UPDATE issues SET data_source_id = NULL WHERE jira_project_id IS NOT NULL;

-- Replace the single unique constraint with two partial unique indexes,
-- one per issue source type.
ALTER TABLE issues DROP CONSTRAINT uk_issue_source_external_id;

CREATE UNIQUE INDEX uq_issues_github_external
    ON issues (data_source_id, external_id)
    WHERE data_source_id IS NOT NULL;

CREATE UNIQUE INDEX uq_issues_jira_external
    ON issues (jira_project_id, external_id)
    WHERE jira_project_id IS NOT NULL;

-- Remove project_key from data_source_configs; it now lives in jira_projects.
ALTER TABLE data_source_configs DROP COLUMN project_key;

-- ADR-005 option C: repo-mapping link table.
-- Allows Jira project issues to count toward Git repository metrics.
CREATE TABLE jira_project_repo_mappings (
    jira_project_id BIGINT NOT NULL REFERENCES jira_projects (id) ON DELETE CASCADE,
    repository_id   BIGINT NOT NULL REFERENCES git_repositories (id) ON DELETE CASCADE,
    PRIMARY KEY (jira_project_id, repository_id)
);

-- rollback:
--   DROP TABLE IF EXISTS jira_project_repo_mappings;
--   ALTER TABLE data_source_configs ADD COLUMN project_key VARCHAR(255);
--   DROP INDEX IF EXISTS uq_issues_jira_external;
--   DROP INDEX IF EXISTS uq_issues_github_external;
--   ALTER TABLE issues ADD CONSTRAINT uk_issue_source_external_id UNIQUE (data_source_id, external_id);
--   UPDATE issues SET data_source_id = jp.data_source_id FROM jira_projects jp WHERE issues.jira_project_id = jp.id;
--   ALTER TABLE issues DROP COLUMN jira_project_id;
--   ALTER TABLE issues ALTER COLUMN data_source_id SET NOT NULL;
--   DROP TABLE IF EXISTS user_project_registrations;
--   DROP TABLE IF EXISTS jira_projects;
