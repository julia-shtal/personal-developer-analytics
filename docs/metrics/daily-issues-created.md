# Daily Issues Created

**Framework:** SPACE
**Category:** Activity
**Unit:** issues per day
**Data source(s):** issues (Jira issues and GitHub Issues, unified)
**Privacy class:** individual
**Granularity:** day

## Definition

The number of issues created in the repositories and Jira projects the user is subscribed to on a given calendar day. This is a project-level metric: it counts all issues created in the tracked projects regardless of who created them, reflecting the workload intake rate of the user's team or project. It is not filtered by author identity.

## Formula

```
FOR each calendar day D in [from, to):
  daily_issues_created(D) =
    COUNT(*)
    FROM issues
    WHERE repository_id IN :repoIds
      AND created_at >= D 00:00:00 UTC
      AND created_at  < D+1 00:00:00 UTC
```

- **No author filter**: issues are project-level. Both Jira issues and GitHub Issues are collected into the unified `issues` table and counted together.
- Time window: UTC calendar day boundaries applied to `created_at`.
- Bot exclusion: applied at the project level (issues created by `[bot]` accounts in GitHub are filtered by the `IssueCollector`). Jira automation issues are included unless explicitly excluded by the Jira JQL scope.
- `repoIds`: the set of `git_repositories` linked to the user's subscriptions. For Jira, the link is through `jira_project_repo_mappings`.

## Edge cases

- **No subscriptions**: if `repoIds` is empty, `calcDailyIssues` returns early; no snapshots are written.
- **Jira issues without a mapped repo**: if a Jira project has no entry in `jira_project_repo_mappings`, its issues are not counted (they have no `repository_id`).
- **Duplicate issues**: each issue has a unique `(jira_project_id, external_id)` or `(repository_id, external_id)` composite key; the upsert on collection prevents duplicates.
- **Reopened issues**: `created_at` is the original creation timestamp. Reopening does not increment the count.

## Validation (thesis §8.3)

- **Expected range**: 0–20 issues/day for an active project; higher values may indicate bulk imports or automation-generated issues.
- **Comparison baseline**: Jira board "Created" report or GitHub Issues list filtered by date and repository.
- **Controlled-change test**: create exactly 3 issues in a tracked project on a specific day → `daily_issues_created` must equal 3 after the next collection run.
