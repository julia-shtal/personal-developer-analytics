# Daily Issues Closed

**Framework:** SPACE
**Category:** Performance / Efficiency
**Unit:** issues closed per day
**Data source(s):** issues (Jira issues and GitHub Issues, unified)
**Privacy class:** individual
**Granularity:** day

## Definition

The number of issues closed in the repositories and Jira projects the user is subscribed to on a given calendar day. Like `DAILY_ISSUES_CREATED`, this is a project-level metric: it measures throughput at the project scope rather than attributing closures to a specific developer. A consistently positive gap between issues closed and issues created signals a healthy, backlog-reducing project.

## Formula

```
FOR each calendar day D in [from, to):
  daily_issues_closed(D) =
    COUNT(*)
    FROM issues
    WHERE repository_id IN :repoIds
      AND closed_at IS NOT NULL
      AND closed_at >= D 00:00:00 UTC
      AND closed_at  < D+1 00:00:00 UTC
```

- Attribution: GitHub issues by `assignee_github_id = user.githubUserId`; Jira issues by `assignee_account_id = user.jiraAccountId`. Closures are credited to the assignee, not to whoever performed the close. See [author-attribution.md](author-attribution.md).
- Bot exclusion: implicit. Attribution matches on the numeric GitHub account ID alone, which no bot account shares with a user, so no bot filter is applied. See [author-attribution.md](author-attribution.md).
- Time window: UTC calendar day boundaries applied to `closed_at`. See [timezone.md](timezone.md).
- Only issues with `closed_at IS NOT NULL` are included.
- Same `repoIds` scope and Jira-linking rules as `DAILY_ISSUES_CREATED`.

## Edge cases

- **No subscriptions**: early return; no snapshots written.
- **Jira issues without mapped repo**: not counted (no `repository_id`).
- **Reopened then re-closed issues**: the latest `closed_at` is used (upsert on collection overwrites the row). An issue re-closed on a different day is counted on the re-close date.
- **Issues closed via commit message** (`fixes #N`): `closed_at` is set by the GitHub API at merge time; the metric reflects that timestamp.

## Validation (thesis §8.3)

- **Expected range**: 0–15 issues/day for a healthy project; sustained 0 over multiple days may indicate planning or review phases.
- **Comparison baseline**: Jira board "Resolved" report or GitHub Issues list `state=closed` filtered by `closed:YYYY-MM-DD..YYYY-MM-DD`.
- **Controlled-change test**: close exactly 2 issues in a tracked project on a specific day → `daily_issues_closed` must equal 2 after the next collection run.
