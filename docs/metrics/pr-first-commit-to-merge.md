# PR First Commit to Merge Lead Time

**Framework:** DORA
**Category:** Performance (Lead Time for Changes — code path)
**Unit:** hours (median)
**Data source(s):** github_pull_requests + git_commits
**Privacy class:** individual
**Granularity:** period

## Definition

The median elapsed time in hours from the earliest commit in a pull request to the pull request being merged, for all pull requests authored by the user and merged within the specified date window. This metric measures the actual coding-to-delivery cycle duration — the time from when the first line of code was committed to when it landed in the main branch. It complements `PR_LEAD_TIME_HOURS_MEDIAN` (which measures PR creation to merge) by including time spent coding before the PR was opened.

## Formula

```
FOR each merged PR P authored by user in [from, to):
  first_commit_date = MIN(c.author_date)
    FROM git_commits c
    WHERE c.repository_id = P.repository_id
      AND c.message references P.number   -- via findCommitsForPr query

  IF first_commit_date IS NULL OR P.merged_at IS NULL:
    SKIP

  first_commit_to_merge_hours(P) =
    FLOOR_TO_HOUR(P.merged_at - first_commit_date)

pr_first_commit_to_merge_lead_time =
  MEDIAN(first_commit_to_merge_hours(P))
  grouped per repository
```

- Attribution: `author_login = user.githubLogin`. **Requires `User.githubLogin` to be set.**
- Window: `merged_at >= from` AND `merged_at < to+1`.
- `findCommitsForPr(repository, prNumber)` joins `git_commits` to the PR via commit hash associations stored during collection. If no commits are linked to the PR, the PR is skipped.
- Duration: `Duration.between(commits.get(0).getAuthorDate(), pr.getMergedAt()).toHours()` — `commits` is ordered by `authorDate` ascending; index 0 is the earliest.
- The computed `leadTimeHours` is also persisted on the `GitHubPullRequestEntity` row (`pr.setLeadTimeHours(hours)`) as a denormalized cache.
- Saved as aggregate shape: `periodFrom = fromDate`, `periodTo = toDate`.
- Bot exclusion: `author_login NOT LIKE '%[bot]'`.

## Edge cases

- **`githubLogin` not set**: metric is skipped entirely.
- **No commits linked to PR**: the PR is skipped (commits list is empty). This occurs when a PR was collected before its commits were fully synced.
- **`mergedAt = null`**: PR skipped; only merged PRs are counted.
- **First commit predates PR creation**: possible for PRs created after the work was already done (e.g. "upstream first, PR later" workflow). The metric correctly captures total elapsed code time.
- **Squash merges**: squash commits appear with `merged_at` as `authorDate`; first commit may still be the feature branch commit. Both timestamps are present; the gap reflects branch lifetime.
- **Negative duration**: if `commits.get(0).getAuthorDate()` is after `merged_at` (data quality issue), the result is negative. The implementation saves the raw value.

## Validation (thesis §8.3)

- **Expected range**: always ≥ `PR_LEAD_TIME_HOURS_MEDIAN` for the same window (first-commit date ≤ PR creation date). Typical delta: 4–48 hours of pre-PR coding time.
- **Comparison baseline**: for a specific PR, check the first commit's `authorDate` in `git log` and compare to the PR's merge timestamp on GitHub.
- **Controlled-change test**: push one commit (T=0), wait 24 hours, open a PR, wait 24 more hours, merge → metric must equal 48.

## References

Forsgren, N., Humble, J., & Kim, G. (2018). *Accelerate: The Science of Lean Software and DevOps*. IT Revolution. (DORA Lead Time for Changes — extended code-path definition.)
