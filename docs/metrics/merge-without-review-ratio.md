# Merge Without Review Ratio

**Framework:** DORA / SPACE
**Category:** Quality / Stability (Change Failure Rate proxy)
**Unit:** ratio (0.0–1.0)
**Data source(s):** github_pull_requests + github_pr_reviews
**Privacy class:** individual
**Granularity:** period

## Definition

The fraction of pull requests authored by the user, merged within the selected date window, that had zero recorded reviews. A review is any entry in `github_pr_reviews` linked to the PR — approvals, change requests, and comments all count. A higher ratio indicates a pattern of merging code without peer review, which is associated with higher change failure rates (Forsgren, Humble & Kim, 2018). This metric complements `REVIEW_RESPONSE_TIME_HOURS_MEDIAN`: where that metric measures how fast reviews happen, this one measures whether they happen at all.

## Formula

```
merged_prs = github_pull_requests
  WHERE repository_id IN :repoIds
    AND author_login  = user.githubLogin
    AND merged_at    >= from AND merged_at < to+1
    AND author_login NOT LIKE '%[bot]'

prs_with_reviews = { pr.id : pr in merged_prs
                     WHERE EXISTS(SELECT 1 FROM github_pr_reviews WHERE pull_request_id = pr.id) }

merge_without_review_ratio =
  (COUNT(merged_prs) - COUNT(prs_with_reviews))
  / COUNT(merged_prs)
  grouped per repository_id
```

Implementation:
```java
Set<Long> prsWithReviews = prReviewRepository
    .findFirstReviewTimestampsByPrIds(prIds)
    .stream().map(PrReviewTimestampProjection::getPrId)
    .collect(Collectors.toSet());

// perRepo[repoId] = [noReviewCount, totalCount]
for (PR pr : prs) {
    counts[1]++;  // total
    if (!prsWithReviews.contains(pr.getId())) counts[0]++;  // no review
}
ratio = counts[0] / counts[1];
```

- Attribution: `author_login = user.githubLogin`. **Requires `User.githubLogin` to be set.**
- Window: `merged_at >= from` AND `merged_at < to+1`.
- Denominator: total merged PRs. No denominator guard for zero: if no merged PRs exist, method returns after the empty-list check.
- Bot exclusion: `author_login NOT LIKE '%[bot]'`.
- Saved as aggregate shape: `periodFrom = fromDate`, `periodTo = toDate`, one snapshot per repository.

## Edge cases

- **`githubLogin` not set**: metric skipped; no snapshot written.
- **No merged PRs in window**: no snapshot written (not a 0.0 value).
- **All PRs reviewed**: `COUNT(prs_with_reviews) = COUNT(merged_prs)` → ratio = 0.0.
- **All PRs unreviewed**: ratio = 1.0.
- **Review data not yet collected**: if PR review collection ran but returned no rows, those PRs are treated as unreviewed (same as reviewed-but-no-data). This is a known limitation: if the GitHub API rate limit prevented review collection, the ratio will be inflated.
- **Self-reviews**: a PR reviewed only by its author still counts as "reviewed" (a row exists in `github_pr_reviews`).

## Validation (thesis §8.3)

- **Expected range**: 0.0–0.2 for a team with active review culture; >0.5 is a risk signal warranting investigation.
- **Comparison baseline**: GitHub pull request list filtered by author and merged date — check each PR manually for review events.
- **Controlled-change test**: merge 3 PRs, 2 of which have no reviews → ratio must equal 0.667.

## References

Forsgren, N., Humble, J., & Kim, G. (2018). *Accelerate: The Science of Lean Software and DevOps*. IT Revolution. (DORA Change Failure Rate; code review as a quality gate.)
