-- T1.3: Remove the denormalized data_source_id column from user_repo_registrations.
-- The relationship user → datasource is derivable via user_repo_registrations → git_repositories → data_source_configs.

ALTER TABLE user_repo_registrations DROP COLUMN data_source_id;

-- rollback:
--   ALTER TABLE user_repo_registrations
--       ADD COLUMN data_source_id BIGINT REFERENCES data_source_configs (id) ON DELETE SET NULL;
