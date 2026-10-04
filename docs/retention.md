# Metrics retention

A nightly job (`RetentionScheduler`) deletes computed metrics data older than a configurable
horizon.

## What's deleted

Once a row's period ends before the horizon:

- `metric_snapshots` — every computed metric value, personal and team-scoped alike.
- `metric_coverage` — the per-day ledger of which personal metrics have been computed.
- `metric_summaries` — stored AI summaries, personal and team-scoped alike.

## What's kept, always

Raw ingested history — commits, pull requests, and issues — is **never** deleted by this job, no
matter how old. It is the only source metrics can be recomputed from, so removing it would make
recomputation impossible if the horizon is later raised or retention is disabled. It is also the
audit trail: a computed figure should always be traceable back to the activity it came from.

## Reads respect the horizon too

Deletion is not the only enforcement point. Two read paths clamp or filter against the same
horizon so a request never sees expired data, even on the day the property changes and before the
nightly job has run again:

- `MetricSnapshotService` clamps every windowed read's `from` forward to the horizon. A window that
  ends before the horizon is then empty and returns nothing; it never reaches the covering-window
  fallback, which would otherwise match a weekly row that straddles the horizon.
- `MetricSummaryPersistenceService` hides any stored summary (latest or history) whose period
  ends before the horizon.

The backfill's gap-scan (`MetricBackfillService`) also stops at the horizon, so it never
recomputes a day the nightly job is about to delete again.

## Changing it

Property: `app.retention.metrics-months` (env var `RETENTION_METRICS_MONTHS`). Default: **24**.
Setting it to **0** disables retention entirely — nothing is deleted, and no read is clamped.
