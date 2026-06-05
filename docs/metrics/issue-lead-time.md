# Issue Lead Time

**Framework:** SPACE
**Category:** Efficiency / Performance
**Unit:** hours (median)
**Data source(s):** issues (Jira and GitHub Issues, unified)
**Privacy class:** individual
**Granularity:** period

## Definition

The median elapsed time in hours between an issue being created and being closed, for all issues in the repositories and Jira projects the user is subscribed to that were closed within the specified date window. This is a project-level metric — it reflects the throughput speed of the team or project as a whole, not the individual developer's resolution speed. A shorter median indicates faster issue resolution cycles.

## Formula

```
FOR each closed issue I in subscribed repos/projects, closed in [from, to):
  lead_time_hours(I) = FLOOR_TO_HOUR(I.closed_at - I.created_at)

issue_lead_time_hours_median =
  MEDIAN(lead_time_hours(I))
  grouped per repository_id
```

- **No author filter**: issues are not attributed to a single developer.
- Window: `closed_at >= from` AND `closed_at < to+1`. `created_at` may fall before the window.
- Duration: `Duration.between(createdAt, closedAt).toHours()`.
- Grouping: one snapshot per repository. Jira issues are linked to a repository via `jira_project_repo_mappings`; issues without a `repository_id` are excluded.
- Saved as aggregate shape: `periodFrom = fromDate`, `periodTo = toDate`.

## Edge cases

- **No subscriptions / empty repoIds**: early return; no snapshot written.
- **Issues without a mapped repository**: Jira issues not linked to a repo via `jira_project_repo_mappings` have `repository_id = NULL` and are excluded from this calculation.
- **Reopened issues**: `closed_at` reflects the most recent closure (the upsert on collection overwrites). Lead time may be shorter than the true elapsed lifecycle if the issue was previously closed and reopened.
- **`created_at > closed_at`**: should not occur; if it does (data quality issue), `Duration.toHours()` returns a negative value which the implementation saves as-is. These should appear as anomalous values in the UI.
- **Median calculation**: same algorithm as `PR_LEAD_TIME_HOURS_MEDIAN` — sorted list, middle element for odd n, average of two middle elements for even n.

## Validation (thesis §8.3)

- **Expected range**: 24–336 hours (1–14 days) for a typical feature-tracking project; bug-fix projects tend toward the lower end.
- **Comparison baseline**: Jira "Time to Resolution" report or GitHub Issues closed date range — manually compute median of `closed_at - created_at`.
- **Controlled-change test**: create one issue and close it after exactly 72 hours → metric must equal 72 with only that issue in the window.
