# Daily Issues Created

**Framework:** SPACE
**Category:** Activity
**Unit:** issues per day
**Data source(s):** issues (Jira issues and GitHub Issues, unified)
**Privacy class:** individual
**Granularity:** day

## Definition

The number of issues the user created on a given calendar day, across the repositories and Jira projects they have registered or are subscribed to. A GitHub issue counts when the user's numeric GitHub account ID is recorded as its creator; a Jira issue counts when the user's Jira `accountId` is recorded as its reporter. Issues filed by anyone else in the same projects are not counted. The metric measures how much work the user raises, not the intake rate of their team.

## Formula

```
FOR each calendar day D in [from, to):
  daily_issues_created(D) =
    COUNT(*)
    FROM issues i
    LEFT JOIN jira_project_repo_mappings rm ON rm.jira_project_id = i.jira_project_id
    WHERE COALESCE(i.repository_id, rm.repository_id) IN :repoIds
      AND ( (i.source = 'GITHUB' AND i.creator_github_id   = user.githubUserId)
         OR (i.source = 'JIRA'   AND i.reporter_account_id = user.jiraAccountId) )
      AND i.created_at >= D 00:00:00 UTC
      AND i.created_at  < D+1 00:00:00 UTC
```

- Attribution: GitHub issues by `creator_github_id = user.githubUserId`; Jira issues by `reporter_account_id = user.jiraAccountId`. Both sources are collected into the unified `issues` table and counted together. See [author-attribution.md](author-attribution.md).
- Time window: UTC calendar day boundaries applied to `created_at`. See [timezone.md](timezone.md).
- Bot exclusion: implicit. Attribution matches on a numeric account identifier alone — the user's GitHub account ID or their Jira `accountId` — which no bot account shares with a user, so no bot filter is applied. Jira automation issues are reported under the automation rule's own `accountId` and are likewise never attributed to a user. See [author-attribution.md](author-attribution.md).
- `repoIds`: the set of `git_repositories` linked to the user's subscriptions. For Jira, the link is through `jira_project_repo_mappings`.

## Edge cases

- **No subscriptions**: if `repoIds` is empty, `DailyIssuesCalculator.calculate` returns early; no snapshots are written.
- **User with no linked identity**: a user who has neither a GitHub account ID nor a Jira `accountId` cannot match either branch of the predicate, so `DailyIssuesCalculator.calculate` returns early and writes nothing at all — not zeros. `DailyIssuesCalculator` produces `DAILY_ISSUES_CREATED` and `DAILY_ISSUES_CLOSED` together, so the early return suppresses both series. Linking at least one account in Settings is a precondition for this metric.
- **Jira issues without a mapped repo**: a Jira issue carries `repository_id = NULL` by construction, so the mapping row is what supplies the repository the `COALESCE` scopes on. A Jira project with no entry in `jira_project_repo_mappings` coalesces to NULL, matches no `repoIds` entry, and its issues are not counted.
- **Duplicate issues**: each issue has a unique `(jira_project_id, source_issue_key)` or `(data_source_id, source_issue_key)` composite key; the upsert on collection prevents duplicates.
- **Reopened issues**: `created_at` is the original creation timestamp. Reopening does not increment the count.
- **Multiple repos**: one snapshot per repo per day; read side sums across repos.

## Validation (thesis §8.3)

- **Expected range**: not yet re-derived. The figure of 0–20 issues/day recorded here described project-wide intake, before attribution narrowed the metric to issues the user created. A per-user range must be measured against real data before it is quoted.
- **Comparison baseline**: Jira board "Created" report filtered to the user as reporter, or GitHub Issues list filtered by `author:<login>` and date. `<login>` must be the user's *current* GitHub login: the metric matches on the numeric account ID, so a rename desynchronises the baseline.
- **Controlled-change test**: create exactly 3 issues **as the user** in a tracked project on a specific day, and at least one issue as a different account → `daily_issues_created` must equal 3 after the next collection run, not 4.
