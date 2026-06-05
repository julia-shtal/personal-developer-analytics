# Daily PRs Created

**Framework:** DORA / SPACE
**Category:** Activity / Performance
**Unit:** pull requests per day
**Data source(s):** github_pull_requests
**Privacy class:** individual
**Granularity:** day

## Definition

The number of pull requests opened by the user on a given calendar day, across all GitHub repositories they have registered or are subscribed to. This metric captures the user's contribution initiation rate and is a proxy for the start of the review and integration cycle. It is distinct from `DAILY_PR_MERGED`, which measures completion.

## Formula

```
FOR each calendar day D in [from, to):
  daily_pr_created(D) =
    COUNT(*)
    FROM github_pull_requests
    WHERE repository_id IN :repoIds
      AND author_login  = user.githubLogin    -- attribution guard
      AND created_at   >= D 00:00:00 UTC
      AND created_at    < D+1 00:00:00 UTC
      AND author_login NOT LIKE '%[bot]'      -- bot exclusion
```

- Attribution: `author_login = user.githubLogin`. **Requires `User.githubLogin` to be set.** If unset, `calcDailyPrs` returns immediately and no snapshots are written.
- Bot exclusion: `author_login` ending with `[bot]` excluded.
- Includes PRs in any state (open, closed, merged) as long as `created_at` falls within the window. A PR that is later rejected still represents initiated work.
- Time window: UTC calendar day boundaries applied to `created_at`.

## Edge cases

- **`githubLogin` not set**: metric is skipped entirely — no 0-valued snapshots are written. The UI displays "—" until the user configures their GitHub login in Settings.
- **Draft PRs**: counted; draft state is not filtered. Draft PRs represent initiated work and their creation is a valid activity signal.
- **Reopened PRs**: `created_at` is the original creation timestamp. Reopening does not generate a new event in the current data model.
- **Multiple repos**: one snapshot per repo per day; read side sums across repos.

## Validation (thesis §8.3)

- **Expected range**: 0–5 PRs created per day for a typical developer; >5/day may indicate automated PR generation or very granular branching.
- **Comparison baseline**: GitHub's "Contributions" graph on the user's profile page or the GitHub API `GET /repos/{owner}/{repo}/pulls?creator=<login>&state=all`.
- **Controlled-change test**: open exactly 2 PRs on a specific day → `daily_pr_created` for that day must equal 2 after the next collection run.
