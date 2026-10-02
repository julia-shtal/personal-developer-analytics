# Merges to Default Branch per Week

**Framework:** DORA
**Category:** Performance (Deployment Frequency proxy)
**Unit:** merges per ISO calendar week
**Data source(s):** github_pull_requests + git_repositories (default_branch)
**Privacy class:** individual
**Granularity:** period (one row per ISO calendar week, per repository)

## Definition

The number of pull requests authored by the user that were merged into the repository's
**default branch**, bucketed by ISO calendar week (Monday–Sunday). This is a proxy, not a
measurement of deployments: a merge to the default branch is not the same event as a release
or a deploy, and this metric makes no claim about what happens to the code after the merge.

This metric exists because an earlier metric, `MERGE_TO_MAIN_FREQUENCY_PER_WEEK`, carried the
same DORA deployment-frequency label while its calculator actually averaged *commit* counts
per week, not PR merges — the label didn't match what the calculator actually measured. That
metric was renamed to `COMMITS_PER_WEEK_AVG` and the DORA label was dropped. This metric is
the deliberate redo: it counts actual PR merge events, and it filters them to merges whose
base branch matches the repository's current default branch, so a merge into a feature or
release branch does not inflate the count.

It is also deliberately narrower than `DAILY_PR_MERGED`, which counts every merged PR per day
regardless of target branch. `DAILY_PR_MERGED` answers "how often does this person merge
something"; this metric answers "how often does this person's work land on the branch that
represents the shipped mainline" — the two can diverge for a team that merges feature branches
into release branches before those reach the default branch.

## Formula

```
merged_prs = github_pull_requests p
  WHERE p.repository_id   IN :repoIds
    AND p.author_github_id = user.githubUserId
    AND p.merged           = true
    AND p.merged_at        >= from AND p.merged_at < to
    AND p.base_branch       = p.repository.default_branch

FOR each merged PR:
  week_start = ISO week Monday of (merged_at converted to a UTC calendar day)
  per_repo_per_week[repo_id][week_start] += 1

FOR each (repo_id, week_start) with count > 0:
  WRITE metric_snapshots row:
    periodFrom = week_start (Monday), periodTo = week_start + 6 days (Sunday)
    value      = count
```

- Attribution: `author_github_id = user.githubUserId`. **Requires a linked GitHub account.**
  If absent the calculator returns immediately and no snapshots are written. See
  [author-attribution.md](author-attribution.md).
- Base-branch filter: `p.base_branch = p.repository.default_branch` is a plain SQL equality
  comparison. SQL equality is false — not true — when either side is `NULL`, so a PR or
  repository with an unknown branch is excluded rather than assumed to match. No fallback
  logic treats a missing value as a match.
- Window: `merged_at >= from` AND `merged_at < to` (half-open, matching the convention used by
  the other author-scoped pull request queries in `GitHubPullRequestRepository`).
- Bucketing: the calculator does not rely on `MetricsService`'s per-week dispatch
  (`aggregatePeriod = false` on `MetricType.MERGES_TO_DEFAULT_BRANCH_PER_WEEK`). It fetches all
  matching merges for the full requested window in one query, converts each `merged_at` to a
  UTC calendar day, derives that day's ISO week (Monday as day 1 via `WeekFields.ISO`), and
  groups counts by `(repository, week)` in memory before writing. Each `(repository, week)`
  pair becomes exactly one `metric_snapshots` row, written through `MetricSnapshotWriter`.
- Saved as aggregate shape: `periodFrom` = that week's Monday, `periodTo` = that week's Sunday,
  one snapshot per `(repository, week)` pair.
- Reduction across weeks: `SUM` (`AggregateWindowResolver`) — weekly counts are disjoint
  events, so they add across a wider reporting window.
- Bot exclusion: implicit. Attribution matches on the numeric GitHub account ID alone, which no
  bot account shares with a user, so no bot filter is applied. See
  [author-attribution.md](author-attribution.md).
- Endpoint: `GET /api/metrics/merges-to-default-branch-per-week?from=&to=&repoId=` (`repoId`
  optional), returns `MetricAggregateDto`.
- Not included in the AI summary context (`inAiContext = false`).

## Edge cases

- **GitHub account not linked**: calculator returns before querying; no snapshot written.
- **Repository's default branch not yet known**: `git_repositories.default_branch` is
  populated lazily — from GitHub's repository API for GitHub repos, or from the local HEAD
  branch for local repos — the next time that repository is synced. Until then it is `NULL`,
  the equality comparison fails, and merges to that repository are excluded from this metric
  entirely (not assumed to target the default branch).
- **PR's base branch not yet known**: `github_pull_requests.base_branch` is captured from
  GitHub's PR list response (`base.ref`) at ingest time, going forward only. A PR ingested
  before this column existed keeps `base_branch = NULL`. This is a known limitation: that PR
  stays excluded from this metric until its repository is resynced *and* that specific PR's data
  changes — incremental sync skips PRs whose `updated_at` hasn't moved, so an old, already-
  merged PR with a NULL `base_branch` may never be re-visited. There is no dedicated backfill
  job for this column.
- **PR merged into a non-default branch**: correctly excluded — this is the entire point of
  the `base_branch = default_branch` filter, distinguishing this metric from `DAILY_PR_MERGED`.
- **No merges in window**: no snapshot written for that repository/week, not a `0.0` value —
  consistent with how other count-based metrics in this codebase (e.g. `DAILY_PR_MERGED`,
  `MERGE_WITHOUT_REVIEW_RATIO`) treat an empty result.
- **Multiple repositories**: one snapshot per repository per week; the read side sums across
  repositories (and across weeks, via the `SUM` reduction).

## Validation (thesis §8.3)

- **Expected range**: 0–5 merges per week per repository for an individual contributor on an
  actively developed repository. A sustained 0 despite known merge activity likely indicates a
  `default_branch`/`base_branch` population gap (see Edge cases), not genuine inactivity.
- **Comparison baseline**: GitHub's pull request list, filtered to merged, authored by the
  test user, and manually checked against the repository's actual default branch (e.g. via the
  repository's own GitHub settings page) for each PR in the window.
- **Controlled-change test**: merge PRs authored by the test user into the default branch on
  known dates straddling an ISO week boundary (e.g. one merge on a Monday of week W, one on the
  following Monday of week W+1) → each week's snapshot count is hand-computable from the known
  merge dates, since ISO week boundaries (Monday start) are fixed and do not depend on
  timezone beyond the UTC-calendar-day conversion already applied to `merged_at`.

## References

Forsgren, N., Humble, J., & Kim, G. (2018). *Accelerate: The Science of Lean Software and
DevOps*. IT Revolution. (DORA Deployment Frequency definition.)
