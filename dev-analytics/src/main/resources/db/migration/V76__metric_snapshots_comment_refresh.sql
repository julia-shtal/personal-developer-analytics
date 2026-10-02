-- V76: refresh the metric_snapshots table comment to add MERGES_TO_DEFAULT_BRANCH_PER_WEEK
-- (AGGREGATE shape, aggregatePeriod=false) under the AGGREGATE section. COMMENT ON TABLE has
-- no partial form and V66 is frozen, so the comment is restated in full.
-- Comment-only — no DDL, no data change, and re-running it is a no-op.

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
           REVIEW_RESPONSE_TIME_HOURS_MEDIAN, REVIEW_PARTICIPATION_COUNT,
           AFTER_HOURS_COMMIT_RATIO, DEEP_WORK_STREAK_DAYS, KNOWLEDGE_SILO_SCORE,
           REFACTOR_RATIO, PR_SIZE_COMPLEXITY_SCORE, MERGE_WITHOUT_REVIEW_RATIO,
           COMMITS_PER_WEEK_AVG, WIP_OPEN_PR_AGE_HOURS_MEDIAN,
           MERGES_TO_DEFAULT_BRANCH_PER_WEEK.
  The lists are exhaustive and disjoint: 8 DAILY + 14 AGGREGATE = 22 MetricType values.
Storage shape is a property of the calculator, not of MetricType.aggregatePeriod, which
selects ISO-week window resolution and is true for only five of the AGGREGATE types.
MERGES_TO_DEFAULT_BRANCH_PER_WEEK is AGGREGATE-shape with aggregatePeriod=false: its
calculator buckets ISO weeks itself and writes one row per (repository, week) pair, rather
than being dispatched once per week by MetricsService.
All writes go through MetricSnapshotWriter (upsert guard). team_id NULL = personal.';
