# Daily Commits Count

**Framework:** SPACE
**Category:** Activity
**Unit:** commits per day
**Data source(s):** git_commits
**Privacy class:** individual
**Granularity:** day

## Definition

The number of commits authored by the user on a given calendar day, across all repositories they have registered or are subscribed to. Each commit is counted at most once regardless of how many branches it appears on. This metric serves as the primary activity signal on the personal dashboard and as the per-member series on the team view.

## Formula

```
FOR each calendar day D in [from, to):
  daily_commits_count(D) =
    COUNT(*)
    FROM git_commits
    WHERE repository_id IN :repoIds
      AND author_email  = user.email          -- attribution guard
      AND author_date  >= D 00:00:00 UTC
      AND author_date   < D+1 00:00:00 UTC
      AND author_name NOT LIKE '%[bot]%'      -- bot exclusion
```

- Time window: calendar day boundaries in UTC.
- Attribution: `author_email = user.email` (case-sensitive match as stored by JGit / GitHub API).
- Bot exclusion: `author_name` ending with `[bot]` is excluded. Applied in metric formula, not at ingestion.
- Result: one `MetricSnapshot` row per (user, repo, day) triplet where count > 0. Days with zero commits produce no row.

## Edge cases

- **Zero commits in window**: no snapshots are saved; the read-side aggregation returns an empty series (not 0).
- **Merge commits**: counted; no distinction is made between merge commits and regular commits. Squash-merge produces one commit attributed to the merger's email.
- **Multiple repos**: one snapshot per repo per day. The read-side sums across repos per day when no `repoId` filter is applied.
- **Email mismatch**: if the user's `User.email` differs from the `author_email` stored in `git_commits` (e.g. a secondary Git identity), those commits are not counted. Users must ensure their primary email is configured in their Git client or update `User.email` in Settings.

## Validation (thesis §8.3)

- **Expected range**: 0–30 commits/day for a typical developer; >10/day consistently may indicate granular commit habits or automation.
- **Comparison baseline**: cross-check against `git log --author=<email> --after=<from> --before=<to> --oneline | wc -l` on the local repository.
- **Controlled-change test**: add exactly 3 commits to a registered repository on a single day → `daily_commits_count` for that day must equal 3 the next time metrics are recalculated.
