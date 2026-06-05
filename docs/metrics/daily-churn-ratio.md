# Daily Churn Ratio

**Framework:** SPACE
**Category:** Quality
**Unit:** ratio (0.0–1.0)
**Data source(s):** git_commits
**Privacy class:** individual
**Granularity:** day

## Definition

The fraction of changed lines that are deletions on a given calendar day, computed across all commits authored by the user. A high churn ratio indicates that most of the day's editing activity involved removing or replacing existing code rather than writing new code. Persistent high churn may indicate rework, bug-fixing, or active refactoring. A ratio near 0 indicates predominantly additive work. Note that this metric measures raw line-level churn, not semantic change quality.

## Formula

```
FOR each calendar day D in [from, to):
  total_additions = SUM(additions)  FROM git_commits WHERE author_email = user.email AND day = D
  total_deletions = SUM(deletions)  FROM git_commits WHERE author_email = user.email AND day = D
  total_changes   = total_additions + total_deletions

  daily_churn_ratio(D) =
    IF total_changes > 0
      THEN total_deletions / total_changes
      ELSE 0.0                             -- denominator guard; no snapshot saved when 0 commits
```

- Attribution: `author_email = user.email`.
- Bot exclusion: `author_name NOT LIKE '%[bot]%'`.
- Denominator guard: if `total_changes = 0` (no commits or all enrichment still PENDING), no snapshot is saved for that day.
- For GitHub-sourced commits: enrichment via `CommitStatsEnrichmentScheduler` must complete before `additions`/`deletions` are reliable. Until then, both are 0 and the ratio would be 0.0. The implementation saves the snapshot with whatever stats are available; the value is provisional until all commits on that day reach `stats_status = COMPLETE`.

## Edge cases

- **All commits PENDING**: additions = 0, deletions = 0 → `total_changes = 0` → snapshot skipped (denominator guard prevents divide-by-zero).
- **Pure deletion commits** (e.g. file removal): ratio = 1.0 for those commits; pulls the daily ratio toward 1.
- **Merge commits** with no diff: contribute 0 to both numerator and denominator, neutral effect.
- **Multiple repos**: each repo contributes its own rows; all are summed per day before computing the ratio. One snapshot is saved per (user, repo, day) triplet.

## Validation (thesis §8.3)

- **Expected range**: 0.2–0.5 for mixed feature/maintenance work; >0.6 on refactoring days; <0.2 on green-field feature days.
- **Comparison baseline**: `git log --author=<email> --numstat --after=<from>` — manually sum additions and deletions.
- **Controlled-change test**: push one commit with 0 additions and 10 deletions → `daily_churn_ratio` for that day must equal 1.0. Push one commit with 10 additions and 10 deletions → ratio must equal 0.5.
