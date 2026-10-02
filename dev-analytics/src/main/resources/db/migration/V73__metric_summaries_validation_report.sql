-- Per-rule validation outcome (dropped-insight counts, final insight count, range check) recorded
-- when a summary is parsed, so a stored insight is always traceable to the rule that let it through.
--
-- Nullable with no backfill: rows written before this change predate validation entirely, so null
-- means "not validated," a state distinct from an empty report and not safe to manufacture.

ALTER TABLE metric_summaries ADD COLUMN validation_report TEXT;

-- rollback:
-- ALTER TABLE metric_summaries DROP COLUMN IF EXISTS validation_report;
