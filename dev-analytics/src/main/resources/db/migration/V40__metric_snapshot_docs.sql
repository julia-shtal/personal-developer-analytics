-- T5.1: Make the metric_snapshots dual-shape storage model self-documenting at the DB level.
-- Pure-comment migration — no DDL, no data changes. Running this migration produces only
-- pg_description entries; no schema-diff tool will report structural changes.
--
-- Two shapes share this table:
--   DAILY     — period_from IS NULL AND period_to IS NULL
--               date = the calendar day measured; value = single-day figure
--   AGGREGATE — period_from IS NOT NULL AND period_to IS NOT NULL
--               date = snapshot capture date; period_from/period_to = calculation window

COMMENT ON TABLE metric_snapshots IS
'Persisted metric output. Two structurally distinct row shapes share this table:
  DAILY (period_from IS NULL, period_to IS NULL):
    date = calendar day measured; value = single-day figure.
    Types: DAILY_COMMITS_COUNT, DAILY_COMMITS_AVG_SIZE, DAILY_PR_CREATED,
           DAILY_PR_MERGED, DAILY_ISSUES_CREATED, DAILY_ISSUES_CLOSED,
           DAILY_CHURN_RATIO, FOCUS_RATIO_DAYS_TASKS.
  AGGREGATE (period_from IS NOT NULL, period_to IS NOT NULL):
    date = snapshot capture date; period_from/period_to = calculation window.
    Types: PR_LEAD_TIME_HOURS_MEDIAN, ISSUE_LEAD_TIME_HOURS_MEDIAN,
           PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN,
           REVIEW_RESPONSE_TIME_HOURS_MEDIAN, AFTER_HOURS_COMMIT_RATIO,
           DEEP_WORK_STREAK_DAYS, KNOWLEDGE_SILO_SCORE, REFACTOR_RATIO,
           PR_SIZE_COMPLEXITY_SCORE, MERGE_WITHOUT_REVIEW_RATIO,
           MERGE_TO_MAIN_FREQUENCY_PER_WEEK.
All writes go through MetricsService.saveMetric (upsert guard). team_id NULL = personal.';

COMMENT ON COLUMN metric_snapshots.date IS
'DAILY: the calendar day measured. AGGREGATE: the snapshot capture date (not the window boundary).';

COMMENT ON COLUMN metric_snapshots.period_from IS
'NULL for DAILY rows. Inclusive start of the calculation window for AGGREGATE rows.';

COMMENT ON COLUMN metric_snapshots.period_to IS
'NULL for DAILY rows. Inclusive end of the calculation window for AGGREGATE rows.';

COMMENT ON COLUMN metric_snapshots.team_id IS
'NULL = personal metric (user''s own data sources). NOT NULL = team-scoped metric.';

-- rollback:
--   COMMENT ON TABLE metric_snapshots IS NULL;
--   COMMENT ON COLUMN metric_snapshots.date IS NULL;
--   COMMENT ON COLUMN metric_snapshots.period_from IS NULL;
--   COMMENT ON COLUMN metric_snapshots.period_to IS NULL;
--   COMMENT ON COLUMN metric_snapshots.team_id IS NULL;
