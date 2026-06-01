-- Add headline field (added to DTO in PDA-45, missed in entity)
ALTER TABLE metric_summaries ADD COLUMN headline TEXT;

-- Add team_id to support persisting team summaries
ALTER TABLE metric_summaries
  ADD COLUMN team_id BIGINT REFERENCES teams(id) ON DELETE CASCADE;

-- Make user_id nullable so team-scoped rows can have user_id = NULL
ALTER TABLE metric_summaries ALTER COLUMN user_id DROP NOT NULL;

-- Upsert key: one row per {user, team, period, scope, context_name}.
-- NULL values are treated as distinct in standard UNIQUE, so COALESCE in a
-- functional index is used instead.
CREATE UNIQUE INDEX uix_metric_summaries_identity
  ON metric_summaries (
    COALESCE(user_id, -1),
    COALESCE(team_id, -1),
    period_from,
    period_to,
    scope,
    COALESCE(context_repo_name, '')
  );

CREATE INDEX ix_metric_summaries_team_generated
  ON metric_summaries (team_id, generated_at DESC);

-- rollback:
-- DROP INDEX ix_metric_summaries_team_generated;
-- DROP INDEX uix_metric_summaries_identity;
-- ALTER TABLE metric_summaries ALTER COLUMN user_id SET NOT NULL;
-- ALTER TABLE metric_summaries DROP COLUMN team_id;
-- ALTER TABLE metric_summaries DROP COLUMN headline;
