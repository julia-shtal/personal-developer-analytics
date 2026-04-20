-- GitHub login needed to attribute PRs from team-scoped repos to individual users.
ALTER TABLE users ADD COLUMN github_login VARCHAR(255);