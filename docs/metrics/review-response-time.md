# Review Response Time

**Framework:** SPACE
**Category:** Communication / Efficiency
**Unit:** hours (median)
**Data source(s):** github_pull_requests + github_pr_reviews
**Privacy class:** individual
**Granularity:** period

## Definition

The median elapsed time in hours between a pull request being opened by the user and receiving its first review, for all pull requests authored by the user that were merged within the specified date window and had at least one recorded review. This metric quantifies how quickly the team responds to code review requests. A shorter review response time indicates a more collaborative, low-latency review culture, which correlates with higher software delivery performance (Forsgren et al., 2021).

## Formula

```
FOR each merged PR P authored by user in [from, to):
  first_review_at = MIN(r.submitted_at)
    FROM github_pr_reviews r
    WHERE r.pull_request_id = P.id

  IF first_review_at IS NULL OR P.created_at IS NULL:
    SKIP   -- PR had no reviews; excluded from metric (not counted as 0)

  IF first_review_at < P.created_at:
    SKIP   -- negative duration guard; treat as data quality issue

  response_hours(P) = FLOOR_TO_HOUR(first_review_at - P.created_at)

review_response_time_hours_median =
  MEDIAN(response_hours(P))
  grouped per repository
```

- Attribution: `author_login = user.githubLogin`. **Requires `User.githubLogin` to be set.**
- Window: `P.merged_at >= from` AND `P.merged_at < to+1`.
- PRs without any review row in `github_pr_reviews` are **excluded** from the denominator. This metric measures review speed when reviews happen, not review adoption (which is `MERGE_WITHOUT_REVIEW_RATIO`).
- Negative durations (review timestamp before PR creation) are skipped: `if (hours < 0) continue`.
- Saved as aggregate shape: `periodFrom = fromDate`, `periodTo = toDate`.
- Bot exclusion on PR author: `author_login NOT LIKE '%[bot]'`.

## Edge cases

- **`githubLogin` not set**: metric is skipped; no snapshot written.
- **No merged PRs in window**: no snapshot written.
- **All merged PRs had no reviews**: all PRs are skipped → no snapshot written (not a 0 median).
- **Review submitted before PR creation**: skipped (hours < 0 guard). Can occur if the GitHub API returns inconsistent timestamps.
- **Multiple reviews on same PR**: only `MIN(submitted_at)` is used (first review response); subsequent reviews do not affect this metric.
- **PR author reviews their own PR**: self-reviews recorded in `github_pr_reviews` are included in the `MIN` query; this may yield a near-zero response time for self-reviewed PRs.

## Validation (thesis §8.3)

- **Expected range**: 1–24 hours for a high-performing team; >48 hours indicates review bottlenecks or insufficient reviewer availability.
- **Comparison baseline**: for individual PRs, compare the PR creation timestamp to the first review event visible in the GitHub PR timeline.
- **Controlled-change test**: open a PR (T=0), submit one review after exactly 6 hours → metric must equal 6 with that PR as the sole data point.

## References

Forsgren, N., et al. (2021). The SPACE of developer productivity. *Queue*, 19(1), 20–48. (Communication and collaboration dimension.)
