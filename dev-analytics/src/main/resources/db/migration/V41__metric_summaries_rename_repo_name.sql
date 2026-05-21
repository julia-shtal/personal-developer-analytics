-- T5.2: Rename metric_summaries.repo_name → context_repo_name.
-- The old name implied GitHub-only context. The field actually stores a snapshot-in-time
-- scope label (repo name, Jira project key, or team name depending on the summary scope),
-- so the new name is intentionally neutral.
--
-- Note: issues.repo_name was already renamed to source_context by V37 as part of T4.2.
-- There is no remaining repo_name column on the issues table.

ALTER TABLE metric_summaries RENAME COLUMN repo_name TO context_repo_name;

-- rollback:
-- ALTER TABLE metric_summaries RENAME COLUMN context_repo_name TO repo_name;
