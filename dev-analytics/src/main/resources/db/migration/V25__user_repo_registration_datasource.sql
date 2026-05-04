-- Track which data source was used when the subscription was created.
-- Null for subscriptions created via the manual subscribe button (no data-source context).
-- Used by RepoController to show cross-ownership repos under the correct data source.

ALTER TABLE user_repo_registrations
    ADD COLUMN data_source_id BIGINT REFERENCES data_source_configs(id) ON DELETE SET NULL;
