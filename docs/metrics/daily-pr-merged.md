# Daily PRs Merged

**Framework:** DORA
**Category:** Performance (Deployment Frequency proxy)
**Unit:** merged pull requests per day
**Data source(s):** github_pull_requests
**Privacy class:** individual
**Granularity:** day

## Definition

The number of pull requests authored by the user that were merged on a given calendar day. This metric is the closest approximation of DORA's Deployment Frequency available without direct CI/CD pipeline integration. A consistently high merge rate indicates a developer actively delivering completed work to the shared codebase. (Forsgren, Humble & Kim, 2018.)

## Formula

```
FOR each calendar day D in [from, to):
  daily_pr_merged(D) =
    COUNT(*)
    FROM github_pull_requests
    WHERE repository_id IN :repoIds
      AND author_login  = user.githubLogin
      AND merged_at    >= D 00:00:00 UTC
      AND merged_at     < D+1 00:00:00 UTC
      AND state         = 'MERGED'
      AND author_login NOT LIKE '%[bot]'
```

- Attribution: `author_login = user.githubLogin`. **Requires `User.githubLogin` to be set.**
- Time window: UTC calendar day boundaries applied to `merged_at`.
- Only PRs with `state = MERGED` (i.e. `merged_at IS NOT NULL`) are counted.
- Bot exclusion: `author_login` ending with `[bot]` excluded.

## Edge cases

- **`githubLogin` not set**: metric is skipped; no snapshots written.
- **PR closed without merge**: excluded. Only `merged_at IS NOT NULL` rows are counted.
- **Merge by another user**: `author_login` refers to the PR author, not the user who clicked "Merge". A developer who opens a PR and a manager who merges it — the PR counts for the developer's `DAILY_PR_MERGED`, not the manager's.
- **Multiple repos**: one snapshot per repo per day; read side sums.

## Validation (thesis §8.3)

- **Expected range**: 0–3 merged PRs per day; sustained >3/day suggests either very small PRs or a branching strategy with many short-lived branches.
- **Comparison baseline**: GitHub pull request list filtered to merged, date range, and author.
- **Controlled-change test**: merge exactly 1 PR authored by the test user on a specific day → `daily_pr_merged` must equal 1 for that day.

## References

Forsgren, N., Humble, J., & Kim, G. (2018). *Accelerate: The Science of Lean Software and DevOps*. IT Revolution. (DORA Deployment Frequency definition.)
