-- TASK 01 - aggregate window resolution.
--
-- The five aggregatePeriod metric types are now computed on a fixed grain: one row per
-- ISO calendar week, period_from = that week's Monday and period_to = the following
-- Sunday. Rows written before this change carry whatever range the caller happened to
-- pass — most often a single day, from the nightly job.
--
-- Reads no longer demand an exact period match; they collect every stored window the
-- requested range contains. Legacy rows would therefore be read alongside the week rows
-- that now cover the same days. For the median types that only skews the figure slightly,
-- but REVIEW_PARTICIPATION_COUNT sums its windows, so a legacy one-day row would be added
-- on top of the week that already includes it.
--
-- Delete the misaligned rows. Nothing is lost that cannot be recomputed: the next
-- calculation pass over any affected range rewrites these metrics at week grain, and the
-- upsert guard in MetricSnapshotWriter keys on (date, period_from, period_to), so the
-- rebuilt rows do not collide with what remains.
--
-- Scoped to the five aggregatePeriod types only. The other period-storing types keep the
-- grain their calculators write; their reads resolve by containment and report the window
-- actually covered, so misalignment is not an error for them.

DELETE FROM metric_snapshots
WHERE metric_type IN (
        'PR_LEAD_TIME_HOURS_MEDIAN',
        'PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN',
        'ISSUE_LEAD_TIME_HOURS_MEDIAN',
        'REVIEW_RESPONSE_TIME_HOURS_MEDIAN',
        'REVIEW_PARTICIPATION_COUNT'
      )
  AND period_from IS NOT NULL
  AND (EXTRACT(ISODOW FROM period_from) <> 1
       OR period_to <> period_from + 6);
