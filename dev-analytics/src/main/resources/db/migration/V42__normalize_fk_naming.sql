-- T5.3: Standardize FK column names across the schema.
--
-- 1. user_repo_registrations.repo_id → repository_id
--    Aligns with the pattern used by every other FK in the schema (e.g. jira_project_id,
--    data_source_id). The user_accessible_repos view references this column and must be
--    recreated after the rename.
--
-- 2. issues.external_id → source_issue_key
--    "external_id" implied a generic surrogate key; "source_issue_key" is self-describing:
--    it stores the issue identifier as it appears in the upstream system
--    (GitHub: "owner/repo#42", Jira: "PDA-123"). PostgreSQL automatically updates the
--    two partial unique indexes (uq_issues_github_external, uq_issues_jira_external) to
--    reference the renamed column — no index rebuild needed.

-- ── user_repo_registrations ──────────────────────────────────────────────────
-- Rename first, then recreate the view that references this column.

ALTER TABLE user_repo_registrations RENAME COLUMN repo_id TO repository_id;

CREATE OR REPLACE VIEW user_accessible_repos AS

  SELECT dsc.user_id,
         gr.id             AS repo_id,
         gr.data_source_id,
         'OWNED'           AS access_type
  FROM   data_source_configs dsc
  JOIN   git_repositories   gr  ON gr.data_source_id = dsc.id

  UNION

  SELECT urr.user_id,
         gr.id             AS repo_id,
         gr.data_source_id,
         'SUBSCRIBED'      AS access_type
  FROM   user_repo_registrations urr
  JOIN   git_repositories        gr  ON gr.id = urr.repository_id
  JOIN   data_source_configs     dsc ON dsc.id = gr.data_source_id
  WHERE  dsc.user_id <> urr.user_id

  UNION

  SELECT tm.user_id,
         gr.id             AS repo_id,
         gr.data_source_id,
         'TEAM'            AS access_type
  FROM   team_members        tm
  JOIN   data_source_configs dsc ON dsc.team_id = tm.team_id
  JOIN   git_repositories    gr  ON gr.data_source_id = dsc.id

  UNION

  SELECT t.manager_id      AS user_id,
         gr.id             AS repo_id,
         gr.data_source_id,
         'TEAM'            AS access_type
  FROM   teams               t
  JOIN   data_source_configs dsc ON dsc.team_id = t.id
  JOIN   git_repositories    gr  ON gr.data_source_id = dsc.id;

-- ── issues ───────────────────────────────────────────────────────────────────

ALTER TABLE issues RENAME COLUMN external_id TO source_issue_key;

-- rollback:
--   ALTER TABLE issues RENAME COLUMN source_issue_key TO external_id;
--   ALTER TABLE user_repo_registrations RENAME COLUMN repository_id TO repo_id;
--   CREATE OR REPLACE VIEW user_accessible_repos AS <original V38 body with urr.repo_id>;
