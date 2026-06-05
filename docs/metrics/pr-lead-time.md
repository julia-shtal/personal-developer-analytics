# PR Lead Time

**Framework:** DORA
**Category:** Performance (Lead Time for Changes)
**Unit:** hours (median)
**Data source(s):** github_pull_requests
**Privacy class:** individual
**Granularity:** period

## Definition

The median elapsed time in hours between a pull request being created and being merged, for all pull requests authored by the user and merged within the specified date window. This metric is the primary DORA Lead Time for Changes signal available without direct CI/CD integration. A shorter median indicates faster review and merge cycles. (Forsgren, Humble & Kim, 2018.)

## Formula

```
FOR each merged PR P authored by user in [from, to):
  lead_time_hours(P) = CEIL_TO_HOUR(P.merged_at - P.created_at)

pr_lead_time_hours_median =
  MEDIAN(lead_time_hours(P))
  grouped per repository
```

- Attribution: `author_login = user.githubLogin`. **Requires `User.githubLogin` to be set.**
- Window: `merged_at >= from` AND `merged_at < to+1` (inclusive end date). `created_at` may fall before the window.
- Bot exclusion: `author_login NOT LIKE '%[bot]'`.
- Duration: `Duration.between(createdAt, mergedAt).toHours()` — integer hours, rounding down (Java `Duration.toHours()`).
- Grouping: one snapshot per repository. The read side returns the first (lowest) snapshot when no `repoId` filter is specified, or the exact repo snapshot when one is provided.
- Saved as aggregate shape: `periodFrom = fromDate`, `periodTo = toDate` (not a per-day snapshot).

## Edge cases

- **`githubLogin` not set**: metric is skipped; no snapshot written.
- **No merged PRs in window**: no snapshot written for that repo (null on the read side).
- **`mergedAt < createdAt`**: should not occur; GitHub API guarantees `merged_at > created_at`. If encountered, the duration is negative — the implementation saves the raw value; the UI may display it as anomalous.
- **Reopened PRs**: `mergedAt` is the actual merge timestamp; the earlier `createdAt` still counts from original creation. This convention is consistent with DORA guidance: lead time starts when work is initiated, not when it was most recently opened.
- **Median calculation**: `Collections.sort(values)` then `values.get(n/2)` for odd n, average of middle two for even n.

## Validation (thesis §8.3)

- **Expected range**: 4–72 hours (elite-to-high DORA performers); >168 hours (1 week) indicates review bottlenecks.
- **Comparison baseline**: GitHub pull request list: filter by author, state=merged, merged date range; manually compute `merged_at - created_at` and take the median.
- **Controlled-change test**: open a PR and merge it after exactly 48 hours → metric must equal 48 with a single PR in the window.

## References

Forsgren, N., Humble, J., & Kim, G. (2018). *Accelerate: The Science of Lean Software and DevOps*. IT Revolution. (DORA Lead Time for Changes definition.)
