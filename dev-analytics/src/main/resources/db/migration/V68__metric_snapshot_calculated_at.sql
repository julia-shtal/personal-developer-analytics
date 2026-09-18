-- When a snapshot was computed, as an instant.
--
-- A point-in-time metric measures an age ending at the moment of calculation, and the only
-- timestamp on the row was `date`, which carries the calculation window's first day. A
-- reader could not tell when the figure was taken, and an age is meaningless without it.
--
-- Backfilled from `date` so existing rows carry a plausible instant rather than a null the
-- read side would have to special-case; new rows get the real one.

ALTER TABLE metric_snapshots ADD COLUMN calculated_at TIMESTAMPTZ;

UPDATE metric_snapshots SET calculated_at = date::timestamptz WHERE calculated_at IS NULL;

-- rollback:
-- ALTER TABLE metric_snapshots DROP COLUMN IF EXISTS calculated_at;
