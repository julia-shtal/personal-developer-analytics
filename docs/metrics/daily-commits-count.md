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
      AND ( author_github_id = user.githubUserId          -- attribution guard
         OR lower(author_email) IN user.commitEmails )
      AND author_date  >= D 00:00:00 UTC
      AND author_date   < D+1 00:00:00 UTC
      AND author_name NOT LIKE '%[bot]%'      -- bot exclusion
```

- Time window: calendar day boundaries in UTC.
- Attribution: `author_github_id = user.githubUserId` **OR** `lower(author_email) IN user.commitEmails`. Either path alone is sufficient; a commit matching both is counted once. See [author-attribution.md](author-attribution.md).
- Bot exclusion: `author_name` ending with `[bot]` is excluded. Applied in metric formula, not at ingestion.
- Result: one `MetricSnapshot` row per (user, repo, day) triplet where count > 0. Days with zero commits produce no row.

## Edge cases

- **Zero commits in window**: no snapshots are saved; the read-side aggregation returns an empty series (not 0).
- **Merge commits**: counted; no distinction is made between merge commits and regular commits. Squash-merge produces one commit attributed to the merger's identity.
- **Multiple repos**: one snapshot per repo per day. The read-side sums across repos per day when no `repoId` filter is applied.
- **Address not declared**: a commit whose `author_email` is not among the user's declared addresses is still counted when GitHub resolved it to their account (`author_github_id`). It is missed only when neither path matches -- a local-repository commit made with an unregistered address. Further addresses are added under Settings, commit emails.

## Validation (thesis §8.3)

- **Expected range**: 0–30 commits/day for a typical developer; >10/day consistently may indicate granular commit habits or automation.
- **Comparison baseline**: cross-check against `git log --author=<email> --after=<from> --before=<to> --oneline | wc -l` on the local repository, repeated for each declared address. For a GitHub repository the baseline must instead be taken per account rather than per address, since GitHub resolves several addresses to one account.
- **Controlled-change test**: add exactly 3 commits to a registered repository on a single day → `daily_commits_count` for that day must equal 3 the next time metrics are recalculated.
