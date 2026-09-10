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

Attributed via `reviewer_github_id = user.githubUserId`. Users with no linked GitHub account are skipped —
no snapshot is written.

## Bot Exclusion Policy

Bot reviewer accounts cannot appear in the count because the query is scoped to the
authenticated user's GitHub account ID, which is a registered human account. Reviews of
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

## Calculation grain and window resolution

Computed on a fixed grain: **one ISO calendar week**, `periodFrom` = that week's Monday
and `periodTo` = that week's Sunday. A calculation request covering any part of a week
computes that week in full, so a stored period never claims narrower coverage than was
actually measured, and recomputing over a differently-framed range updates the same rows
rather than adding a second window over the same days.

The ISO week is the canonical grain because it matches the weekly summary job and
`COMMITS_PER_WEEK_AVG`, and because it is the smallest window over which a median is not
usually a median of one observation.

Reads do not require the requested window to match a stored one. A request resolves to
every stored week it fully contains; where it contains none — a request narrower than one
week — it resolves to the week that contains it. The response reports the window actually
covered, not the window requested.

Where a request spans several weeks, the reported figure is the **sum of the per-week
counts**. Weekly windows are disjoint and a pull request is counted at most once per
window, so the counts add up exactly.
