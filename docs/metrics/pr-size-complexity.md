# PR Size Complexity Score

**Framework:** SPACE
**Category:** Quality
**Unit:** changed lines per commit (median across merged PRs)
**Data source(s):** github_pull_requests
**Privacy class:** individual
**Granularity:** period

## Definition

The median of `(total changed lines) / (commit count)` across the pull requests authored by the user, merged within the selected date window, and whose size statistics were retrieved, grouped per repository. This score quantifies the average "payload" per commit within a PR and serves as a proxy for review complexity — larger scores indicate that each commit in a PR touches more lines, making individual commits harder to review. A lower score corresponds to smaller, more focused commits and is generally associated with easier code review and lower defect rates.

## Formula

```
FOR each merged PR P authored by user in [from, to)
    WHERE P.stats_status = 'COMPLETE':            -- enriched pull requests only
  size_score(P) = (P.additions + P.deletions) / MAX(P.commits_count, 1)

pr_size_complexity_score =
  MEDIAN(size_score(P))
  grouped per repository_id
```

- Attribution: `author_github_id = user.githubUserId`. **Requires a linked GitHub account** -- a login that has been resolved to its numeric account ID. If absent the calculator returns immediately and no snapshots are written. See [author-attribution.md](author-attribution.md).
- Window: `merged_at >= from` AND `merged_at < to+1`.
- Bot exclusion: implicit. Attribution matches on the numeric GitHub account ID alone, which no bot account shares with a user, so no bot filter is applied. See [author-attribution.md](author-attribution.md).
- `MAX(commits_count, 1)`: guards against squash-merged PRs where `commits_count = 0` (the PR appears as a single synthesised commit after squash). Treating such PRs as 1 commit prevents division by zero and reflects the squash as a single-commit review unit.
- Population: enriched pull requests only, `stats_status = 'COMPLETE'`, applied in the calculator over the shared merged-PR query — the same query serves `MERGE_WITHOUT_REVIEW_RATIO` and `REVIEW_RESPONSE_TIME_HOURS_MEDIAN`, which count every merged PR and must not be narrowed.
- `additions` and `deletions` are populated during two-phase GitHub stats enrichment. A PR with `stats_status = PENDING` carries both as placeholder zeros and is excluded rather than admitted as a `size_score` of 0: a zero the median never measured would drag it toward 0 for as long as the backlog lasts. The median therefore describes the enriched merged PRs of a window, which may be fewer than the window's merged PRs.
- Median calculation: `Collections.sort(values)` then middle element (odd n) or average of two middle elements (even n).
- Saved as aggregate shape: `periodFrom = fromDate`, `periodTo = toDate`, one snapshot per repository.

## Edge cases

- **GitHub account not linked**: metric skipped; no snapshot written.
- **No merged PRs in window**: no snapshot written.
- **Squash merge with `commits_count = 0`**: treated as 1 commit (denominator guard). `size_score = additions + deletions`.
- **Unenriched PRs (`stats_status = PENDING`)**: excluded from the median.
- **Every merged PR in the window unenriched**: the population is empty and no snapshot is written — the same outcome as a window with no merged PRs at all. The value appears once `StatsEnrichmentScheduler` has worked through the backlog.
- **Single PR in window**: median equals that PR's score exactly.

## Validation (thesis §8.3)

- **Expected range**: 20–200 changed lines/commit for typical feature PRs; >500 indicates very large commits that are difficult to review.
- **Comparison baseline**: for a specific PR, compute `(additions + deletions) / commits` manually from the GitHub PR diff stats.
- **Controlled-change test**: merge one PR with 100 total changes across 5 commits, enrichment complete → score must equal 20.0. Merge a second PR with 50 total changes as a squash (commits_count = 0 → treated as 1) → scores of [20.0, 50.0] → median = 35.0.

## Population and exclusions

Computed over pull requests whose per-PR size statistics were retrieved — `stats_status = 'COMPLETE'`.
Pull requests whose enrichment was abandoned carry `stats_skip_reason`, which separates the two
causes because they bear on validity in opposite directions: `DIFF_TOO_LARGE` means GitHub refused
to render a diff above its size cap, which is itself evidence about the repository's
pull-request-size distribution, while `RECORD_UNAVAILABLE` means the detail endpoint returned 404
or 422, which is evidence about API access and says nothing about the pull request. `UNKNOWN` marks
rows skipped before the two were distinguished. `GET /api/metrics/stats-coverage` reports each count
for a window, so the size of the exclusion can be quoted rather than asserted.

`DIFF_TOO_LARGE` is expected to be near-zero in practice. GitHub documents *Get a pull request* as
returning 200, 304, 404, 406, 500 or 503 — neither 403 nor 422 is a documented response. A genuinely
oversized pull request therefore surfaces as repeated 5xx that exhaust the retry budget and lands in
`FAILED`, which the coverage endpoint reports as its own group. The exclusion is four counts, not
three.
