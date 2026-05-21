-- T4.5: Add base_url_normalized to jira_projects and replace the per-datasource
-- unique constraint with a global one, mirroring git_repositories.repo_full_name UNIQUE
-- (ADR-004 / ADR-002). Prevents the same (Jira instance, project key) from being
-- registered as separate rows under different datasources.

ALTER TABLE jira_projects
    ADD COLUMN IF NOT EXISTS base_url_normalized VARCHAR(255);

-- Backfill from each row's datasource base_url, lowercased with trailing slashes stripped.
UPDATE jira_projects jp
SET    base_url_normalized = lower(regexp_replace(dsc.base_url, '/+$', ''))
FROM   data_source_configs dsc
WHERE  jp.data_source_id = dsc.id
  AND  dsc.base_url IS NOT NULL
  AND  jp.base_url_normalized IS NULL;

-- Sentinel for legacy rows whose datasource had no base_url. Operator must backfill manually.
UPDATE jira_projects
SET    base_url_normalized = '__unknown__'
WHERE  base_url_normalized IS NULL;

ALTER TABLE jira_projects
    ALTER COLUMN base_url_normalized SET NOT NULL;

-- Replace the per-DS constraint with a global one.
ALTER TABLE jira_projects
    DROP CONSTRAINT IF EXISTS uq_jira_project_ds_key;

ALTER TABLE jira_projects
    ADD CONSTRAINT uq_jira_project_global
        UNIQUE (base_url_normalized, project_key);

-- rollback:
--   ALTER TABLE jira_projects DROP CONSTRAINT IF EXISTS uq_jira_project_global;
--   ALTER TABLE jira_projects
--       ADD CONSTRAINT uq_jira_project_ds_key UNIQUE (data_source_id, project_key);
--   ALTER TABLE jira_projects ALTER COLUMN base_url_normalized DROP NOT NULL;
--   ALTER TABLE jira_projects DROP COLUMN IF EXISTS base_url_normalized;
