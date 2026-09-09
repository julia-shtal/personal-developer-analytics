-- V57: rename MERGE_TO_MAIN_FREQUENCY_PER_WEEK to COMMITS_PER_WEEK_AVG.
--
-- The type name claimed a count of merges to the default branch; the
-- calculator averages commit counts per ISO calendar week. The name is
-- corrected to match the computation.
--
-- MetricType is persisted with @Enumerated(EnumType.STRING), so stored rows
-- carry the constant name as text. Renaming the constant without this
-- migration leaves rows that MetricType.valueOf() cannot resolve. Both
-- statements are idempotent: a second run matches nothing.

UPDATE metric_snapshots
   SET metric_type = 'COMMITS_PER_WEEK_AVG'
 WHERE metric_type = 'MERGE_TO_MAIN_FREQUENCY_PER_WEEK';

UPDATE user_goals
   SET metric_type = 'COMMITS_PER_WEEK_AVG'
 WHERE metric_type = 'MERGE_TO_MAIN_FREQUENCY_PER_WEEK';

-- V40 wrote the old constant name into the metric_snapshots table comment
-- (pg_description). Migrations are forward-only, so V40 cannot be edited; the
-- comment is restated here in full with the corrected name. Comment-only —
-- no DDL, no data change.
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
           COMMITS_PER_WEEK_AVG.
All writes go through MetricsService.saveMetric (upsert guard). team_id NULL = personal.';
