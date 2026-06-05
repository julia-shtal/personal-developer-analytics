# Refactor Ratio

**Framework:** SPACE
**Category:** Quality
**Unit:** ratio (0.0–1.0)
**Data source(s):** git_commits (requires `stats_status = COMPLETE`)
**Privacy class:** individual
**Granularity:** period

## Definition

The fraction of the user's fully-enriched commits in the selected window where the number of deleted lines exceeds the number of added lines. Deletions-exceed-additions is used as a proxy for refactoring or code-reduction work — the idea being that a commit that shrinks the codebase is more likely to be removing duplication, dead code, or complexity than adding new features. The metric is restricted to commits where GitHub diff stats have been successfully enriched (`stats_status = COMPLETE`) to avoid the denominator being inflated by commits with placeholder zeros.

## Formula

```
enriched_commits = git_commits
  WHERE repository_id IN :repoIds
    AND author_email  = user.email
    AND author_date  >= from AND author_date < to+1
    AND stats_status  = 'COMPLETE'
    AND author_name NOT LIKE '%[bot]%'

refactor_ratio =
  COUNT(enriched_commits WHERE deletions > additions)
  / COUNT(enriched_commits)          -- denominator guard: skip if 0
```

- Attribution: `author_email = user.email`.
- Bot exclusion: `author_name NOT LIKE '%[bot]%'`.
- **`stats_status = COMPLETE` filter**: only applied to the `REFACTOR_RATIO` denominator (and numerator). `AFTER_HOURS_COMMIT_RATIO` uses the same query but applies its count to all commits regardless of stats status (since it only needs `author_date`, which is always available).
- If `enrichedTotal = 0` (no COMPLETE commits in window), the snapshot is **not saved** to avoid persisting a misleading 0.0.
- Saved as aggregate shape: `periodFrom = fromDate`, `periodTo = toDate`, `repository = null`.

## Edge cases

- **All commits still PENDING**: `enrichedTotal = 0` → no snapshot written. The value becomes available once `CommitStatsEnrichmentScheduler` processes the backlog.
- **Local Git repos**: all commits have accurate stats immediately (JGit reads diffs directly), so `stats_status = COMPLETE` is set at collection time.
- **`deletions == additions`**: not counted as a refactor commit. The threshold is strictly `deletions > additions`.
- **Merge commits with no diff**: `additions = 0`, `deletions = 0` → `deletions > additions` is false → not counted as a refactor.
- **Zero commits in window**: method returns early if `rows.isEmpty()`; no snapshot written.

## Validation (thesis §8.3)

- **Expected range**: 0.1–0.4 during normal development; >0.5 during dedicated refactoring or technical-debt reduction sprints.
- **Comparison baseline**: `git log --author=<email> --numstat` — count commits where total deletions > total additions.
- **Controlled-change test**: push one commit with 5 additions and 10 deletions (`stats_status = COMPLETE`) and one commit with 10 additions and 5 deletions → `refactor_ratio` must equal 0.5.
