# WIP Open PR Age

**Framework:** DORA
**Category:** Flow / Work-in-Progress
**Unit:** hours (median across open PRs)
**Data source(s):** github_pull_requests
**Privacy class:** individual
**Granularity:** point-in-time (stored with period bounds)

## Definition

The median age, in hours, of pull requests authored by the user that are open — not merged and
not closed — at the moment of metric calculation. Age is measured from each PR's `created_at` to
`Instant.now()` at calculation time. Unlike the lead-time metrics, which measure completed work,
this metric captures work-in-progress accumulation: a rising value means open PRs are queuing up
without being merged or closed. The median is computed per repository, so a `repoId` filter
narrows to a single repo and the cross-repo view takes the median of per-repo values.

## Formula

```
now = Instant.now()   // evaluated once per calculation run

FOR each open PR P authored by user (merged_at IS NULL AND closed_at IS NULL):
  age_hours(P) = HOURS_BETWEEN(P.created_at, now)

wip_open_pr_age_hours_median =
  MEDIAN(age_hours(P))
  grouped per repository_id
```

- Attribution: `author_github_id = user.githubUserId`. **Requires a linked GitHub account** -- a login that has been resolved to its numeric account ID. If absent the calculator returns immediately and no snapshots are written. See [author-attribution.md](author-attribution.md).
- Openness: `merged_at IS NULL AND closed_at IS NULL`. No date-range filter — a PR opened before
  the reporting window still counts if it is open now.
- Scope: only PRs in repositories within the user's current repo scope (`repository_id IN scope`).
- Bot exclusion: automated authors (`dependabot[bot]`, `renovate[bot]`, `github-actions[bot]`) are
  excluded by the `author_github_id = user.githubUserId` filter — a bot account is never the user's.
- Age is truncated to whole hours (`Duration.toHours()`); the median reuses
  `CalcUtils.medianOfLongs`.
- Saved as aggregate shape: `periodFrom = fromDate`, `periodTo = toDate`, one snapshot per
  repository. A value of 0 in the UI (`—`) means no open PRs.

## Edge cases

- **GitHub account not linked**: metric skipped; no snapshot written.
- **Empty repo scope**: metric skipped; no snapshot written.
- **No open PRs in a repo**: no snapshot written for that repo (not a zero-value save).
- **Single open PR in a repo**: median equals that PR's age exactly.
- **Backfill over a past window**: age is still measured against the current `Instant.now()`, not
  the historical window — the metric is inherently point-in-time.

## Validation (thesis §8.3)

- **Expected range**: 0–72 hours for a healthy flow; sustained values >120 hours indicate a WIP
  queue that is not being drained.
- **Controlled-change test**: with two open PRs created 24h and 72h before `now` in one repo →
  ages `[24, 72]` → median = 48.0.
