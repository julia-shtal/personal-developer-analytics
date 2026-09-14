# Daily Issues Closed

**Framework:** SPACE
**Category:** Performance / Efficiency
**Unit:** issues closed per day
**Data source(s):** issues (Jira issues and GitHub Issues, unified)
**Privacy class:** individual
**Granularity:** day

## Definition

The number of issues assigned to the user that were closed on a given calendar day, across the repositories and Jira projects they have registered or are subscribed to. A GitHub issue counts when the user's numeric GitHub account ID is its assignee; a Jira issue counts when the user's Jira `accountId` is its assignee. Closures are credited to the assignee rather than to whoever performed the close — neither source records the closing actor — so an issue someone else closes on the user's behalf counts for the user, and an issue the user closes for someone else does not. Read against `DAILY_ISSUES_CREATED`, the two series describe different roles: issues the user raised versus issues assigned to them. Their difference is a rough balance of intake against completion for one person, not a personal backlog figure.

## Formula

```
FOR each calendar day D in [from, to):
  daily_issues_closed(D) =
    COUNT(*)
    FROM issues i
    LEFT JOIN jira_project_repo_mappings rm ON rm.jira_project_id = i.jira_project_id
    WHERE COALESCE(i.repository_id, rm.repository_id) IN :repoIds
      AND ( (i.source = 'GITHUB' AND i.assignee_github_id  = user.githubUserId)
         OR (i.source = 'JIRA'   AND i.assignee_account_id = user.jiraAccountId) )
      AND i.closed_at IS NOT NULL
      AND i.closed_at >= D 00:00:00 UTC
      AND i.closed_at  < D+1 00:00:00 UTC
```

- Attribution: GitHub issues by `assignee_github_id = user.githubUserId`; Jira issues by `assignee_account_id = user.jiraAccountId`. Closures are credited to the assignee, not to whoever performed the close. See [author-attribution.md](author-attribution.md).
- Bot exclusion: implicit. Attribution matches on a numeric account identifier alone — the user's GitHub account ID or their Jira `accountId` — which no bot account shares with a user, so no bot filter is applied. See [author-attribution.md](author-attribution.md).
- Time window: UTC calendar day boundaries applied to `closed_at`. See [timezone.md](timezone.md).
- Only issues with `closed_at IS NOT NULL` are included.
- Same `repoIds` scope and Jira-linking rules as `DAILY_ISSUES_CREATED`.

## Edge cases

- **No subscriptions**: early return; no snapshots written.
- **User with no linked identity**: a user who has neither a GitHub account ID nor a Jira `accountId` cannot match either branch of the predicate, so `DailyIssuesCalculator.calculate` returns early and writes nothing at all — not zeros. `DailyIssuesCalculator` produces `DAILY_ISSUES_CREATED` and `DAILY_ISSUES_CLOSED` together, so the early return suppresses both series.
- **Jira issues without mapped repo**: not counted. A Jira issue carries `repository_id = NULL` by construction; the `jira_project_repo_mappings` row, not the column, is what attaches it to a repository, and without one the coalesced repository is NULL.
- **No assignee**: a closed issue with no assignee, or one assigned to an account no user has linked, matches no user and is counted for nobody. Summing the metric over all users is therefore a lower bound on the project's closed-issue count, not equal to it.
- **Multiple GitHub assignees**: collection stores GitHub's single `assignee` field only. On an issue with several assignees the primary assignee is credited and the secondary ones are not counted.
- **Reopened then re-closed issues**: the latest `closed_at` is used (upsert on collection overwrites the row). An issue re-closed on a different day is counted on the re-close date. The same upsert overwrites the assignee, so attribution follows the *current* assignee: reassigning an issue that has already been counted moves it to the new assignee on the next collection run, retroactively.
- **Issues closed via commit message** (`fixes #N`): `closed_at` is set by the GitHub API at merge time; the metric reflects that timestamp.
- **Multiple repos**: one snapshot per repo per day; read side sums across repos.

## Validation (thesis §8.3)

- **Expected range**: not yet re-derived. The figure of 0–15 issues/day recorded here described project-wide throughput, before attribution narrowed the metric to issues assigned to the user. A per-user range must be measured against real data before it is quoted.
- **Comparison baseline**: Jira board "Resolved" report filtered to the user as assignee, or GitHub Issues search `is:closed assignee:<login> closed:YYYY-MM-DD..YYYY-MM-DD`. `<login>` must be the user's *current* GitHub login: the metric matches on the numeric account ID, so a rename desynchronises the baseline.
- **Controlled-change test**: close exactly 2 issues **assigned to the user** in a tracked repository or Jira project on a specific day, and at least one issue in the same repository or project assigned to a different account → `daily_issues_closed` must equal 2 after the next collection run, not 3.
