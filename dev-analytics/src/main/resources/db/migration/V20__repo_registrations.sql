-- Tracks which user explicitly registered which git repository.
-- Decouples "who registered this repo" from "which data_source_config owns the row",
-- so personal metrics work even when a GitHub repo row is owned by a team data source.

CREATE TABLE user_repo_registrations (
    id      BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    repo_id BIGINT NOT NULL REFERENCES git_repositories(id) ON DELETE CASCADE,
    UNIQUE (user_id, repo_id)
);

CREATE INDEX idx_user_repo_reg_user ON user_repo_registrations(user_id);
