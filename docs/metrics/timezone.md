# Timezones and Day Boundaries

**Applies to:** every metric in this directory
**Data source(s):** users.timezone, metric_snapshots.date, metric_coverage.date

This page is the canonical definition of *which clock decides what "today" means*. Each metric
document states which rule it uses and refers here for the rule itself, so the definition exists
once rather than in nineteen partial copies. It is the time-side counterpart to
[author-attribution.md](author-attribution.md).

## Principle

A figure computed from the same input history must not change because the platform was deployed on
a machine in a different country. Wherever a calendar day has to be derived from an instant, the
zone is stated explicitly; nothing reads the JVM default.

Two different zones are correct in two different places, and conflating them is the failure this
page exists to prevent.

| Clock | Zone | Decides |
|---|---|---|
| **System clock** | always UTC | when a scheduled job fires, and which day it treats as "yesterday" |
| **Attribution clock** | the user's `users.timezone` | which of *that user's* calendar days an activity belongs to, and what counts as their working hours |

The system clock answers "what day is it for the platform". The attribution clock answers "what day
was it for this developer". A developer in Auckland finishes a working day roughly thirteen hours
before a UTC-only reading agrees that it has finished; both statements are true of different
questions.

## The system clock

`SystemClock` wraps `Clock.system(ZoneOffset.UTC)` and is the only place a current instant enters
the application. Its no-argument `today()` and `yesterday()` read UTC.

Every cron-scheduled job declares `zone = "UTC"`, so the firing time is a property of the
configuration rather than of the deployment:

| Job | Schedule |
|---|---|
| `MetricsScheduler` | 01:00 UTC daily — computes yesterday for every user |
| `TokenCleanupScheduler` | 02:00 UTC daily — deletes expired refresh and reset tokens |
| `MetricBackfillScheduler` | 03:00 UTC daily — fills gaps in each user's collected history |
| `MetricsSummaryScheduler` | 08:00 UTC Mondays — generates the weekly AI summary |

Jobs declared with `fixedRate` or `fixedDelay` (`StatsEnrichmentScheduler`,
`SyncJobTracker`) carry no zone, because an interval has no wall-clock anchor to interpret.

## The attribution clock

`UserZone.of(user)` is the single reading of `users.timezone`. Two metrics and the backfill window
depend on it:

| Site | Uses the user's zone for |
|---|---|
| `AFTER_HOURS_COMMIT_RATIO` | classifying each commit's wall-clock hour and weekday |
| `MetricBackfillService` | both ends of the window it fills — the earliest activity day, and the last day considered complete |
| `POST /api/metrics/backfill` | rejecting a requested `to` that is not yet a finished day for the requester |

The backfill guard and the backfill window read the same zone deliberately: otherwise the endpoint
would refuse a day the nightly job already considered complete, or accept one it did not.

### Resolving the zone

`users.timezone` is `VARCHAR(64) NOT NULL DEFAULT 'Europe/Berlin'`. A user who never opens Settings
is therefore attributed in **Europe/Berlin**, not UTC — the column default is the product decision,
and UTC is only a defensive fallback:

| Stored value | Resolves to |
|---|---|
| a valid IANA zone id | that zone |
| unparseable | `ZoneOffset.UTC`, with a warning logged against the user id |
| null or blank | `ZoneOffset.UTC` — unreachable through the database, which rejects null |

DST is handled by `ZoneId` itself: each instant is offset by the rule in force at that instant, so
a window spanning a transition is not skewed.

### Changing a timezone does not recompute anything

Unlike changing an identity, editing `users.timezone` does not invalidate stored snapshots. Rows
already written under the previous zone survive, so an `AFTER_HOURS_COMMIT_RATIO` series can
contain days classified under two different zones. The value is recomputed only when the day is
recalculated for another reason.

This is a deliberate asymmetry: an identity change alters *which records belong to the user*, which
makes every stored figure wrong; a zone change alters only the interpretation of a boundary.

## Storage

`Instant` is used for stored timestamps and at API boundaries; the columns are `timestamptz`
(migrations `V46`, `V55`), so Postgres preserves the offset and comparisons are unambiguous.
`LocalDate` is used only for calendar-day windows — `metric_snapshots.date`, `period_from`,
`period_to` — where the day has already been decided by one of the two clocks above. `LocalDateTime`
is not used anywhere: it is an instant with the zone silently removed.

## Known divergence: daily bucketing follows the database session zone

The daily metrics bucket rows with `date(<timestamptz column>)` in the repository queries. On
PostgreSQL that cast resolves in the **session** `TimeZone`, which the JDBC driver sets from the
JVM default; `hibernate.jdbc.time_zone` is not configured. The day a commit is assigned to
therefore follows the server's zone, not UTC.

Observed directly against the project database for the instant `2026-03-14 23:30:00+00`:

| JVM / session zone | `date(...)` returns |
|---|---|
| `UTC` | `2026-03-14` |
| `America/Los_Angeles` | `2026-03-14` |
| `Pacific/Auckland` | `2026-03-15` |

The windows passed into those queries *are* UTC-explicit (`MetricsService` converts `from`/`to` with
`atStartOfDay(ZoneOffset.UTC)`), so only the bucketing inside the window is affected. The specified
rule for every daily metric remains UTC day boundaries, as each metric document states; the
implementation does not yet meet it, and the affected metrics are:

`DAILY_COMMITS_COUNT`, `DAILY_COMMITS_AVG_SIZE`, `DAILY_CHURN_RATIO`, `DAILY_PR_CREATED`,
`DAILY_PR_MERGED`, `DAILY_ISSUES_CREATED`, `DAILY_ISSUES_CLOSED`, `DEEP_WORK_STREAK_DAYS`,
`FOCUS_RATIO_DAYS_TASKS`.

Two installations in different zones will disagree about which day a commit made near midnight
belongs to. Within one installation the bucketing is self-consistent, so a single deployment's
series is internally comparable.
