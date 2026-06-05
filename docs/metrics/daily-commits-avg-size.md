# Daily Commits Average Size

**Framework:** SPACE
**Category:** Activity / Quality
**Unit:** changed lines per commit (additions + deletions)
**Data source(s):** git_commits (requires `stats_status = COMPLETE`)
**Privacy class:** individual
**Granularity:** day

## Definition

The average number of changed lines (additions plus deletions) per commit authored by the user on a given calendar day. A higher value indicates larger, more sweeping commits; a lower value indicates smaller, more focused changes. This metric depends on GitHub commit stats enrichment being complete — commits with `stats_status = PENDING` or `FAILED` report additions = 0 and deletions = 0, so the average is computed only from enriched commits for GitHub-sourced repositories. Local Git commits always have accurate stats from JGit.

## Formula

```
FOR each calendar day D in [from, to):
  daily_commits_avg_size(D) =
    AVG(additions + deletions)
    FROM git_commits
    WHERE repository_id IN :repoIds
      AND author_email  = user.email
      AND author_date  >= D 00:00:00 UTC
      AND author_date   < D+1 00:00:00 UTC
      AND author_name NOT LIKE '%[bot]%'
```

- Attribution: `author_email = user.email`.
- Bot exclusion: `author_name LIKE '%[bot]%'` excluded.
- The query (`aggregateCommitsDailyByRepoIdsAndAuthorEmail`) returns `AVG(c.additions + c.deletions)` alongside the daily commit count; a single DB round-trip produces both `DAILY_COMMITS_COUNT` and `DAILY_COMMITS_AVG_SIZE`.
- For GitHub-sourced commits, `additions` and `deletions` are populated by `CommitStatsEnrichmentScheduler`. Until enrichment completes, those commits contribute 0 to the average, making the value unreliable. The metric is saved regardless; users should treat values close to 0 for recent days as provisional.

## Edge cases

- **No enriched stats**: if all commits on a day are `PENDING`, the average is 0. The snapshot is still saved; the read side cannot distinguish "genuinely 0-line commits" from "not yet enriched". Consider treating very small values (< 1) as provisional in the UI.
- **Zero commits**: no snapshot saved for that day.
- **Local Git repos**: stats are always complete (JGit reads the diff directly), so `avg_size` is accurate immediately.
- **Merge commits**: merge commits with no diff (fast-forward) contribute 0 additions and 0 deletions, pulling the average down.

## Validation (thesis §8.3)

- **Expected range**: 10–200 changed lines/commit for typical feature work; refactoring sessions may spike to 500+.
- **Comparison baseline**: `git log --author=<email> --stat --after=<from> --before=<to>` and manually compute the average.
- **Controlled-change test**: push a single commit touching exactly 10 lines (5 added, 5 deleted) on a day with no other commits → `daily_commits_avg_size` for that day must equal 10.
