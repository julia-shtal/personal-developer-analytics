# PR Size Complexity Score

**Framework:** SPACE
**Category:** Quality
**Unit:** changed lines per commit (median across merged PRs)
**Data source(s):** github_pull_requests
**Privacy class:** individual
**Granularity:** period

## Definition

The median of `(total changed lines) / (commit count)` across all pull requests authored by the user and merged within the selected date window, grouped per repository. This score quantifies the average "payload" per commit within a PR and serves as a proxy for review complexity — larger scores indicate that each commit in a PR touches more lines, making individual commits harder to review. A lower score corresponds to smaller, more focused commits and is generally associated with easier code review and lower defect rates.

## Formula

```
FOR each merged PR P authored by user in [from, to):
  size_score(P) = (P.additions + P.deletions) / MAX(P.commits_count, 1)

pr_size_complexity_score =
  MEDIAN(size_score(P))
  grouped per repository_id
```

- Attribution: `author_github_id = user.githubUserId`. **Requires a linked GitHub account** -- a login that has been resolved to its numeric account ID. If absent the calculator returns immediately and no snapshots are written. See [author-attribution.md](author-attribution.md).
- Window: `merged_at >= from` AND `merged_at < to+1`.
- Bot exclusion: `author_login NOT LIKE '%[bot]'`.
- `MAX(commits_count, 1)`: guards against squash-merged PRs where `commits_count = 0` (the PR appears as a single synthesised commit after squash). Treating such PRs as 1 commit prevents division by zero and reflects the squash as a single-commit review unit.
- `additions` and `deletions` are populated during two-phase GitHub stats enrichment. PRs with `stats_status = PENDING` have both values as 0, making `size_score = 0`. These are included in the median, which may skew results toward 0 until enrichment completes.
- Median calculation: `Collections.sort(values)` then middle element (odd n) or average of two middle elements (even n).
- Saved as aggregate shape: `periodFrom = fromDate`, `periodTo = toDate`, one snapshot per repository.

## Edge cases

- **GitHub account not linked**: metric skipped; no snapshot written.
- **No merged PRs in window**: no snapshot written.
- **Squash merge with `commits_count = 0`**: treated as 1 commit (denominator guard). `size_score = additions + deletions`.
- **Unenriched PRs (`stats_status = PENDING`)**: `additions = 0`, `deletions = 0` → `size_score = 0`. These pull the median down until enrichment completes.
- **Single PR in window**: median equals that PR's score exactly.

## Validation (thesis §8.3)

- **Expected range**: 20–200 changed lines/commit for typical feature PRs; >500 indicates very large commits that are difficult to review.
- **Comparison baseline**: for a specific PR, compute `(additions + deletions) / commits` manually from the GitHub PR diff stats.
- **Controlled-change test**: merge one PR with 100 total changes across 5 commits → score must equal 20.0. Merge a second PR with 50 total changes as a squash (commits_count = 0 → treated as 1) → scores of [20.0, 50.0] → median = 35.0.
