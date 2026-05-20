-- T4.3: consolidate the three-path "user can access repo" logic from
-- RepoService.listAccessible() into a single database view (ADR-004 Option B).
--
-- Branches:
--   OWNED      — user created the datasource that owns the repo
--   SUBSCRIBED — user has a UserRepoRegistration for a repo owned by someone else
--   TEAM       — user is a member or manager of a team whose datasource owns the repo
--
-- data_source_id is included so callers can filter by datasource without a join.
-- UNION (not UNION ALL) deduplicates across branches automatically.

CREATE VIEW user_accessible_repos AS

  -- 1. Repos where the user OWNS the datasource
  SELECT dsc.user_id,
         gr.id             AS repo_id,
         gr.data_source_id,
         'OWNED'           AS access_type
  FROM   data_source_configs dsc
  JOIN   git_repositories   gr  ON gr.data_source_id = dsc.id

  UNION

  -- 2. Repos the user SUBSCRIBED to via another user's datasource
  SELECT urr.user_id,
         gr.id             AS repo_id,
         gr.data_source_id,
         'SUBSCRIBED'      AS access_type
  FROM   user_repo_registrations urr
  JOIN   git_repositories        gr  ON gr.id = urr.repo_id
  JOIN   data_source_configs     dsc ON dsc.id = gr.data_source_id
  WHERE  dsc.user_id <> urr.user_id

  UNION

  -- 3a. Repos accessible as a TEAM MEMBER
  SELECT tm.user_id,
         gr.id             AS repo_id,
         gr.data_source_id,
         'TEAM'            AS access_type
  FROM   team_members        tm
  JOIN   data_source_configs dsc ON dsc.team_id = tm.team_id
  JOIN   git_repositories    gr  ON gr.data_source_id = dsc.id

  UNION

  -- 3b. Repos accessible as a TEAM MANAGER (manager_id lives in teams, not team_members)
  SELECT t.manager_id      AS user_id,
         gr.id             AS repo_id,
         gr.data_source_id,
         'TEAM'            AS access_type
  FROM   teams               t
  JOIN   data_source_configs dsc ON dsc.team_id = t.id
  JOIN   git_repositories    gr  ON gr.data_source_id = dsc.id;

-- rollback:
-- DROP VIEW IF EXISTS user_accessible_repos;
