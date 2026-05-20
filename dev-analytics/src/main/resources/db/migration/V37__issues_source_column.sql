-- T4.2 (ADR-005 Option C): add a source discriminator to issues and rename
-- repo_name → source_context so the label is no longer GitHub-specific.

ALTER TABLE issues ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'GITHUB';

-- Jira issues have jira_project_id set; everything else is GitHub.
UPDATE issues SET source = 'JIRA' WHERE jira_project_id IS NOT NULL;

ALTER TABLE issues ALTER COLUMN source DROP DEFAULT;

ALTER TABLE issues RENAME COLUMN repo_name TO source_context;

-- rollback:
-- ALTER TABLE issues RENAME COLUMN source_context TO repo_name;
-- ALTER TABLE issues DROP COLUMN source;
