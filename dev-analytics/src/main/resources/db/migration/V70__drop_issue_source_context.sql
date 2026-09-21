-- issues.source_context held a denormalised label — repository full name for GitHub, project
-- key for Jira — that nothing read. It duplicated jira_project_id on a Jira row and
-- repository_id on a GitHub one, both of which remain.
--
-- DROP COLUMN is a catalogue change in Postgres: the row data stays in place until a later
-- rewrite reclaims it, so no table rewrite happens here. Not reversible without the values.

-- migration-guard:confirmed-drop no reader anywhere in the artifact; duplicates jira_project_id
-- and repository_id, both of which remain.
ALTER TABLE issues DROP COLUMN IF EXISTS source_context;
