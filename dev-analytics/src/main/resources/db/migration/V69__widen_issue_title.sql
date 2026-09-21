-- Issue titles are as long as the upstream tracker allows.
--
-- The column kept the JPA default of VARCHAR(255) while its two siblings, git_commits.message
-- and github_pull_requests.title, are TEXT. Jira caps a summary at 255 upstream, so the width
-- was adequate for a Jira-tracked project and silently too narrow for a GitHub-tracked one: a
-- single 550-character title aborted the whole repository's issue collection.
--
-- Postgres treats VARCHAR(n) -> TEXT as a catalog-only change: same on-disk representation, no
-- table rewrite and no index rebuild, so the ACCESS EXCLUSIVE lock covers a catalog edit and
-- nothing more.
--
-- Not reversible without deciding what to do with the rows that outgrew 255; narrowing the type
-- again would truncate them.

ALTER TABLE issues ALTER COLUMN title TYPE TEXT;
