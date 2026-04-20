-- Hybrid data source scoping: GITHUB/JIRA/GITHUB_ISSUES can be owned by a team.
-- GIT_LOCAL stays user-scoped; team_id is always NULL for those.
-- user_id remains set to the creator for audit purposes even when team_id is set.

ALTER TABLE data_source_configs
    ADD COLUMN team_id BIGINT REFERENCES teams(id) ON DELETE CASCADE;

CREATE INDEX idx_datasource_team ON data_source_configs(team_id);
