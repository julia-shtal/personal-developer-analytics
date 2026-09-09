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
- Grouping: one snapshot per repository. With no `repoId` filter the read side reduces across repositories first (median of the per-repository medians for that week), then across weeks; with a `repoId` it reads only that repository's snapshots.
- Saved as aggregate shape: `periodFrom` = the ISO week Monday, `periodTo` = that week Sunday (not a per-day snapshot).

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

## Calculation grain and window resolution

Computed on a fixed grain: **one ISO calendar week**, `periodFrom` = that week's Monday
and `periodTo` = that week's Sunday. A calculation request covering any part of a week
computes that week in full, so a stored period never claims narrower coverage than was
actually measured, and recomputing over a differently-framed range updates the same rows
rather than adding a second window over the same days.

The ISO week is the canonical grain because it matches the weekly summary job and
`COMMITS_PER_WEEK_AVG`, and because it is the smallest window over which a median is not
usually a median of one observation.

Reads do not require the requested window to match a stored one. A request resolves to
every stored week it fully contains; where it contains none — a request narrower than one
week — it resolves to the week that contains it. The response reports the window actually
covered, not the window requested.

Where a request spans several weeks, the reported figure is the **median of the per-week
medians**. Combining sub-window medians is an approximation of the median over the whole
range — the underlying observations are not stored per window — but it is bounded by the
weekly values and is labelled with the window it covers.
