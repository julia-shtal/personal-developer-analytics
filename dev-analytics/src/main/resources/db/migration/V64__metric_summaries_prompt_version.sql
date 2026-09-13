-- Stamp every summary with the prompt that produced it.

ALTER TABLE metric_summaries ADD COLUMN prompt_version VARCHAR(16);

-- Rows written before versioning genuinely cannot be attributed to a prompt; labelling them
-- so keeps them out of any evaluation that compares one prompt against another.
UPDATE metric_summaries SET prompt_version = 'PRE_VERSIONING' WHERE prompt_version IS NULL;

ALTER TABLE metric_summaries ALTER COLUMN prompt_version SET NOT NULL;

-- Widen the upsert key with prompt_version: two prompt versions may now hold a row for the same
-- scope and period, which is what makes a before/after comparison possible.
DROP INDEX uix_metric_summaries_identity;
CREATE UNIQUE INDEX uix_metric_summaries_identity
  ON metric_summaries (
    COALESCE(user_id, -1),
    COALESCE(team_id, -1),
    period_from,
    period_to,
    scope,
    COALESCE(context_repo_name, ''),
    prompt_version
  );

-- rollback:
-- DROP INDEX uix_metric_summaries_identity;
-- CREATE UNIQUE INDEX uix_metric_summaries_identity
--   ON metric_summaries (
--     COALESCE(user_id, -1), COALESCE(team_id, -1), period_from, period_to,
--     scope, COALESCE(context_repo_name, ''));
-- ALTER TABLE metric_summaries DROP COLUMN prompt_version;
