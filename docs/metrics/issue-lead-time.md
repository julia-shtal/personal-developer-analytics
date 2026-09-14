# Issue Lead Time

**Framework:** SPACE
**Category:** Efficiency / Performance
**Unit:** hours (median)
**Data source(s):** issues (Jira and GitHub Issues, unified)
**Privacy class:** individual
**Granularity:** period

## Definition

The median elapsed time in hours between an issue being created and being closed, over the issues assigned to the user that were closed within the specified date window, across the repositories and Jira projects they have registered or are subscribed to. A GitHub issue counts when the user's numeric GitHub account ID is its assignee; a Jira issue counts when the user's Jira `accountId` is its assignee. Lead time is credited to the assignee, so the figure describes the issues this developer owns rather than the throughput of the whole project. It measures the issue's full lifetime from creation to close, not the time since it was assigned — an issue that sat in the backlog before being picked up carries that waiting time into the developer's median. A shorter median indicates faster issue resolution cycles.

## Formula

```
FOR each closed issue I in subscribed repos/projects, closed in [from, to),
    WHERE I is assigned to the user —
      GitHub: I.assignee_github_id  = user.githubUserId
      Jira:   I.assignee_account_id = user.jiraAccountId :

  lead_time_hours(I) = TRUNCATE_TO_HOUR(I.closed_at - I.created_at)

issue_lead_time_hours_median =
  MEDIAN(lead_time_hours(I))
  grouped per COALESCE(I.repository_id, mapped repository of I.jira_project_id)
```

- Attribution: GitHub issues by `assignee_github_id = user.githubUserId`; Jira issues by `assignee_account_id = user.jiraAccountId`. Lead time is credited to the assignee. See [author-attribution.md](author-attribution.md).
- Bot exclusion: implicit. Attribution matches on a numeric account identifier alone — the user's GitHub account ID or their Jira `accountId` — which no bot account shares with a user, so no bot filter is applied. See [author-attribution.md](author-attribution.md).
- Window: `closed_at >= from` AND `closed_at < to+1`. `created_at` may fall before the window.
- Duration: `Duration.between(createdAt, closedAt).toHours()` — the whole hours elapsed, remaining minutes discarded. Truncation is toward zero rather than downward, which differs from flooring only for the negative durations noted below.
- Grouping: one snapshot per repository. A Jira issue carries `repository_id = NULL` by construction and is attached to a repository through `jira_project_repo_mappings`; what is excluded is an issue whose coalesced repository is NULL, that is, a Jira project with no mapping. On the read side a request without a `repoId` reports the median of the per-repository medians — the same approximation applied across weeks below — while a request with a `repoId` reads only that repository's snapshots.
- Saved as aggregate shape: `periodFrom` = the ISO week Monday, `periodTo` = that week Sunday.

## Edge cases

- **No subscriptions / empty repoIds**: early return; no snapshot written.
- **User with no linked identity**: a user who has neither a GitHub account ID nor a Jira `accountId` cannot match either branch of the predicate, so `IssueLeadTimeCalculator.calculate` returns early and writes no snapshot at all — not a zero median.
- **Issues without a mapped repository**: Jira issues not linked to a repo via `jira_project_repo_mappings` have `repository_id = NULL` and are excluded from this calculation.
- **Reopened issues**: `closed_at` reflects the most recent closure (the upsert on collection overwrites). Lead time may be shorter than the true elapsed lifecycle if the issue was previously closed and reopened.
- **`created_at > closed_at`**: should not occur; if it does (data quality issue), `Duration.toHours()` returns a negative value which the implementation saves as-is. These should appear as anomalous values in the UI.
- **No assignee**: a closed issue with no assignee, or one assigned to an account no user has linked, matches no user, so it contributes to no user's median.
- **Multiple GitHub assignees**: collection stores GitHub's single `assignee` field only. On an issue with several assignees the primary assignee is credited and the secondary ones are not.
- **Reassignment**: the upsert on collection overwrites the assignee, so attribution follows the *current* assignee. Reassigning an issue that has already been counted moves its lead time to the new assignee's median on the next collection run, retroactively.
- **Median calculation**: same algorithm as `PR_LEAD_TIME_HOURS_MEDIAN` — sorted list, middle element for odd n, average of two middle elements for even n.

## Validation (thesis §8.3)

- **Expected range**: not yet re-derived. The figure of 24–336 hours recorded here described resolution speed across a whole project, before attribution narrowed the metric to issues assigned to the user. A per-user range must be measured against real data before it is quoted.
- **Comparison baseline**: Jira "Time to Resolution" report filtered to the user as assignee, or GitHub Issues `assignee:<login> state=closed` over the date range — manually compute the median of `closed_at - created_at`. `<login>` must be the user's *current* GitHub login: the metric matches on the numeric account ID, so a rename desynchronises the baseline.
- **Controlled-change test**: create one issue **assigned to the user** and close it after exactly 72 hours, and a second issue assigned to a different account closed after 12 hours → the metric must equal 72, not 42.

## Calculation grain and window resolution

Computed on a fixed grain: **one ISO calendar week**, `periodFrom` = that week's Monday
and `periodTo` = that week's Sunday. A calculation request covering any part of a week
computes that week in full, so a stored period never claims narrower coverage than was
actually measured, and recomputing over a differently-framed range updates the same rows
rather than adding a second window over the same days.

The ISO week is the canonical grain because it matches the weekly summary job and
`COMMITS_PER_WEEK_AVG`, and because it is the smallest window that aggregates more than a
single day. It carries no guarantee of sample size: at per-user scope a weekly median is
often taken over very few closed issues — sometimes one — so a single week's figure reads
as an individual measurement rather than as a distribution.

Reads do not require the requested window to match a stored one. A request resolves to
every stored week it fully contains; where it contains none — a request narrower than one
week — it resolves to the week that contains it. The response reports the window actually
covered, not the window requested.

Where a request spans several weeks, the reported figure is the **median of the per-week
medians**. Combining sub-window medians is an approximation of the median over the whole
range — the underlying observations are not stored per window — but it is bounded by the
weekly values and is labelled with the window it covers.
