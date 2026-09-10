-- TASK 02 - imported history never receives metrics.
--
-- Which calendar days personal metrics have actually been computed for, per user.
--
-- The nightly job previously detected gaps with MAX(metric_snapshots.date), a high-water
-- mark that a capped backfill moved past days it had skipped, losing them permanently.
-- The obvious replacement -- SELECT DISTINCT date FROM metric_snapshots -- does not work
-- either: calculators write a row only when the day produced data, so a day on which the
-- user did not commit yields no row and would be treated as missing on every subsequent
-- run. AGGREGATE rows are worse still, since their `date` is the window start rather than
-- the day measured.
--
-- This table records the computation itself rather than inferring it from the output, so
-- the missing-day set shrinks monotonically and the per-run cap becomes a resumable
-- throttle instead of truncation.

CREATE TABLE metric_coverage (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    date        DATE        NOT NULL,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_metric_coverage_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    CONSTRAINT uq_metric_coverage_user_date UNIQUE (user_id, date)
);

-- No separate index on (user_id, date): the UNIQUE constraint above already
-- creates that btree index, and findDatesInRange's predicate
-- (user_id = ? AND date BETWEEN ? AND ?) is fully served by it. Do not
-- re-add a duplicate index here.

-- Seed from what has already been computed, so an existing installation does not
-- re-derive months of history it already holds. Personal scope only (team_id IS NULL):
-- the ledger tracks the personal calculation path, which is the one the backfill drives.
INSERT INTO metric_coverage (user_id, date)
SELECT DISTINCT user_id, date
FROM   metric_snapshots
WHERE  team_id IS NULL
ON CONFLICT (user_id, date) DO NOTHING;
