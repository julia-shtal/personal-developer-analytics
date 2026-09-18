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
    AVG(CASE WHEN stats_status = 'COMPLETE'
             THEN additions + deletions END)     -- enriched commits only
    FROM git_commits
    WHERE repository_id IN :repoIds
      AND ( author_github_id = user.githubUserId
         OR lower(author_email) IN user.commitEmails )
      AND author_date  >= D 00:00:00 UTC
      AND author_date   < D+1 00:00:00 UTC
      AND author_name NOT LIKE '%[bot]%'
```

- Time window: calendar day boundaries in UTC. See [timezone.md](timezone.md).
- Attribution: `author_github_id = user.githubUserId` **OR** `lower(author_email) IN user.commitEmails`. Either path alone is sufficient; a commit matching both is counted once. See [author-attribution.md](author-attribution.md).
- Bot exclusion: `author_name NOT LIKE '%[bot]%'`, applied in the query. Attribution can match on a declared email address, and a local commit carries no `author_github_id`, so an automation account configured with the user's address would otherwise be attributed to them. See [author-attribution.md](author-attribution.md).
- Read with `GET /api/metrics/daily-commits-avg-size`, optionally narrowed by `repoId`. Without one the per-repository values for a day are **averaged**, not summed: the figure is changed lines per commit, so adding one repository's to another's would report a size no commit had. The mean of per-repository means is unweighted, so it approximates the true cross-repository average unless the repositories contributed equally that day.
- Population: enriched commits only, `stats_status = 'COMPLETE'`. The restriction is applied inside the aggregate rather than in the `WHERE` clause, because `DAILY_COMMITS_COUNT` is read from the same query and counts every attributed commit — counting a commit needs nothing but its date. A day on which no commit is enriched yields a null average.
- The query (`aggregateCommitsDailyByRepoIdsAndIdentity`) returns that conditional average alongside the daily commit count; a single DB round-trip produces both `DAILY_COMMITS_COUNT` and `DAILY_COMMITS_AVG_SIZE`.
- For GitHub-sourced commits, `additions` and `deletions` are populated by `StatsEnrichmentScheduler`. Until enrichment completes, those commits are excluded from the average rather than averaged in as zeros: an unenriched row carries placeholder zeros, and admitting one would fabricate an observation instead of omitting it. The average therefore describes the enriched commits of a day, which may be fewer than the day's commit count.

## Edge cases

- **No enriched stats**: if no commit on a day is `COMPLETE`, the conditional average is null and the snapshot is saved as 0. The read side cannot distinguish that from a day of genuinely 0-line commits, so a value of 0 alongside a non-zero `DAILY_COMMITS_COUNT` should be read as provisional rather than as small commits.
- **Zero commits**: no snapshot saved for that day.
- **Local Git repos**: stats are always complete (JGit reads the diff directly), so `avg_size` is accurate immediately.
- **Merge commits**: merge commits with no diff (fast-forward) contribute 0 additions and 0 deletions, pulling the average down.

## Validation (thesis §8.3)

- **Expected range**: 10–200 changed lines/commit for typical feature work; refactoring sessions may spike to 500+.
- **Comparison baseline**: `git log --author=<email> --stat --after=<from> --before=<to>` and manually compute the average.
- **Controlled-change test**: push a single commit touching exactly 10 lines (5 added, 5 deleted) on a day with no other commits → `daily_commits_avg_size` for that day must equal 10.

## Population and exclusions

Computed over commits whose per-commit statistics were retrieved — `stats_status = 'COMPLETE'`.
Commits whose enrichment was abandoned carry `stats_skip_reason`, which separates the two causes
because they bear on validity in opposite directions: `DIFF_TOO_LARGE` means GitHub refused to
render a diff above its size cap, which is itself evidence about the repository's commit-size
distribution, while `RECORD_UNAVAILABLE` means the detail endpoint returned 404 or 422, which is
evidence about API access and says nothing about the commit. `UNKNOWN` marks rows skipped before
the two were distinguished. `GET /api/metrics/stats-coverage` reports each count for a window, so
the size of the exclusion can be quoted rather than asserted.

`DIFF_TOO_LARGE` is expected to be near-zero in practice. GitHub documents *Get a commit* as
returning 200, 404, 409, 422, 500 or 503 — 403 is not a documented response — and says only that
larger diffs may time out with a 5xx. A genuinely oversized commit therefore surfaces as repeated
5xx that exhaust the retry budget and lands in `FAILED`, which the coverage endpoint reports as its
own group. The exclusion is four counts, not three.
