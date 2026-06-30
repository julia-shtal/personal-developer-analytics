# Code Review Participation

**Metric type:** `REVIEW_PARTICIPATION_COUNT`
**Category:** Collaborative engagement
**Storage shape:** Aggregate period (`period_from` / `period_to`; `date` = snapshot capture date)
**Repository scope:** Cross-repository total (`repository_id = NULL`)
**Team scope:** Personal only (`team_id = NULL`)

## Definition

The count of distinct GitHub pull requests in which the user appears as a reviewer
(state `APPROVED`, `CHANGES_REQUESTED`, or `COMMENTED`) during the calculation window,
across all repositories the user has registered with the platform.

A pull request is counted at most once per calculation window regardless of how many
reviews the user submitted on that PR. Self-reviews (reviewer login equals PR author login)
are excluded.

## Attribution

Attributed via `User.githubLogin`. Users without a `githubLogin` registered are skipped —
no snapshot is written.

## Bot Exclusion Policy

Bot reviewer accounts cannot appear in the count because the query is scoped to the
authenticated user's `githubLogin`, which is a registered human account. Reviews of
bot-authored pull requests (e.g., `dependabot[bot]` dependency bumps) are included in
the count; such reviews reflect genuine human engagement with automated changes.
Ingestion preserves all raw review records for audit.

## Interpretation

A higher value indicates active participation in peer code review. Low values alongside
high PR throughput may indicate a knowledge-silo risk. This metric complements
`MERGE_WITHOUT_REVIEW_RATIO` (which measures PRs the user authored that lacked reviews)
by capturing the reciprocal behaviour.

## Calculation window

Defined by `submittedAt >= from AND submittedAt < to` on the `github_pr_reviews` table.
The `to` boundary is exclusive so adjacent windows do not double-count reviews submitted
exactly at midnight.
