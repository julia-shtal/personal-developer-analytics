# Knowledge Silo Score

**Framework:** SPACE
**Category:** Collaboration / Risk
**Unit:** ratio (0.0–1.0)
**Data source(s):** git_commits
**Privacy class:** individual
**Granularity:** period

## Definition

The maximum fraction of commits in any single repository that belong to the user, computed across all their subscribed repositories for the selected date window. A score of 0.9 means the user authored 90% of all commits in at least one of their repos — a strong bus-factor signal indicating that a critical repository depends almost entirely on one person. A lower score indicates a more evenly distributed knowledge base. This metric is a proxy for the bus-factor risk at the individual level.

## Formula

```
FOR each repo R in :repoIds:
  total_commits(R) =
    COUNT(*) FROM git_commits
    WHERE repository_id = R
      AND author_date >= from AND author_date < to+1

  user_commits(R) =
    COUNT(*) FROM git_commits
    WHERE repository_id = R
      AND ( author_github_id = user.githubUserId
         OR lower(author_email) IN user.commitEmails )
      AND author_date  >= from AND author_date < to+1

  share(R) = user_commits(R) / total_commits(R)   -- denominator guard: skip if total = 0

knowledge_silo_score = MAX(share(R)) over all R with total_commits(R) > 0
```

- Attribution for numerator: `author_github_id = user.githubUserId` **OR** `lower(author_email) IN user.commitEmails`. The denominator counts every author and is not attributed. See [author-attribution.md](author-attribution.md).
- Denominator: all commits regardless of author — no email filter.
- Bot exclusion: **not applied** in the current implementation. Bot commits count toward the denominator (`total_commits`) and, if authored with the user's email (unusual), toward the numerator. This is intentional: bot commits represent real repository activity and dilute the silo score.
- Saved as aggregate shape: `periodFrom = fromDate`, `periodTo = toDate`, `repository = null`.

## Edge cases

- **No commits in any repo**: `totalByRepo` is empty → early return; no snapshot written.
- **User made zero commits in a repo**: `user_commits(R) = 0`; `share = 0.0` for that repo. Does not prevent another repo from contributing a non-zero max.
- **User is the only contributor**: `share = 1.0`; snapshot saved with value 1.0.
- **Single repo**: max is simply that repo's share.
- **Repositories with only bot commits**: total > 0 but user share ≈ 0; these repos will not drive the maximum unless the user also committed there.

## Validation (thesis §8.3)

- **Expected range**: 0.2–0.6 for a developer working in shared codebases; >0.8 indicates a strong solo-maintainer pattern in at least one repo.
- **Comparison baseline**: for a target repo, count commits by author using `git shortlog -sn --after=<from> --before=<to>` and compute each author's fraction.
- **Controlled-change test**: in a repo with 10 existing commits by other authors, add 10 new commits by the test user → score must equal 0.5 for that repo. If it is the only repo, the overall score must equal 0.5.

## References

Forsgren, N., et al. (2021). The SPACE of developer productivity. *Queue*, 19(1), 20–48. (Collaboration dimension — bus-factor and knowledge sharing.)
