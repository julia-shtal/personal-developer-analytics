-- Database-level uniqueness for metric_snapshots.
--
-- The writer's guard (findExisting then insert/update) is read-then-write: two callers
-- computing the same window can both miss findExisting and both insert (MetricWriteGate
-- narrows this to the scheduled writers; request threads, the collect- pool and the
-- attribution listener still write outside it). This closes it for every caller with an
-- expression index on the same identity findExisting already queries by, COALESCE-d to the
-- same sentinel semantics as that query's "IS NOT DISTINCT FROM" on the nullable dimensions.

-- Existing duplicates must be gone before the index can be created. Keep the row with the
-- latest calculated_at per identity, tie-broken by id so the choice is deterministic.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id,
                            COALESCE(team_id, -1),
                            COALESCE(repository_id, -1),
                            date,
                            metric_type,
                            COALESCE(period_from, DATE '0001-01-01'),
                            COALESCE(period_to, DATE '0001-01-01')
               ORDER BY calculated_at DESC NULLS LAST, id DESC
           ) AS rn
    FROM metric_snapshots
)
DELETE FROM metric_snapshots
WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

CREATE UNIQUE INDEX uix_metric_snapshots_identity ON metric_snapshots (
    user_id,
    COALESCE(team_id, -1),
    COALESCE(repository_id, -1),
    date,
    metric_type,
    COALESCE(period_from, DATE '0001-01-01'),
    COALESCE(period_to, DATE '0001-01-01')
);

-- rollback:
-- DROP INDEX IF EXISTS uix_metric_snapshots_identity;
