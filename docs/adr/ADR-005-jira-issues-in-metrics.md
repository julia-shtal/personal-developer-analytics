# ADR-005 — Jira Issues Strategy in the Metric Engine

## Status

Accepted

## Context

Jira issues are collected and stored in the `issues` table with `repository_id = NULL`
and `jira_project_id` set. Every metric query that aggregates issues (`DAILY_ISSUES_CREATED`,
`DAILY_ISSUES_CLOSED`, `ISSUE_LEAD_TIME_HOURS_MEDIAN`) joins `IssueEntity` to
`GitRepositoryEntity` via `i.repository r` and filters `r.id IN :repoIds`. Because
`repository_id` is NULL for all Jira issues, they are silently excluded — every Jira-only
project reports zero for these metrics. This is a validity threat to RQ2 (§8.7 of the thesis).

Three options were considered for how Jira issues should participate in the metric engine:

**A. Drop Jira from metrics.** Keep Jira as a data source for raw issue browsing only;
remove or suppress the three issue metrics for datasources that are purely Jira.

**B. First-class source type.** Add a `source` discriminator column and separate aggregation
query variants per source, so GitHub and Jira issue counts are reported independently.

**C. Map Jira projects to Git repositories.** A `jira_project_repo_mappings` join table
lets a Jira project be associated with one or more Git repositories. The metric engine uses
this mapping to include Jira issues in the repository-scoped aggregations, as if the issues
belonged to the mapped repo.

## Decision

**Option C — map Jira projects to Git repositories.**

Rationale:
- The `jira_project_repo_mappings` table is already present in the schema (V32). The
  infrastructure cost of Option C is therefore already paid.
- The thesis RQ1 frames the platform as cross-source: a single dashboard covering commits,
  PRs, and issues for a *project* (not per-source). Option C preserves that framing — a
  project is represented by a Git repository, and Jira issues are associated to it.
- Option A silently drops valid data; it would widen, not close, the validity gap.
- Option B produces parallel metric series (GitHub issues vs. Jira issues) that would
  require significant frontend changes to surface meaningfully.

## Consequences

**T4.2 (executing task):**
- Add `source VARCHAR(16) NOT NULL` column to `issues`, backfilled from `repository_id IS NOT NULL`
  heuristic (`GITHUB`) and `jira_project_id IS NOT NULL` (`JIRA`).
- Rename `issues.repo_name` → `issues.source_context` (snapshot label; name no longer
  implies GitHub-only).
- Add a `JiraProjectRepoMapping` entity (or use native SQL) so JPA can traverse
  `jira_project_repo_mappings`.
- Update `IssueRepository` aggregation queries to UNION GitHub rows (via `repository_id`) with
  Jira rows routed through `jira_project_repo_mappings → repository_id`, so `repoIds`-scoped
  queries return the combined count for each repo.
- Update `IssueEntity` to add the `source` field and rename `repoName` → `sourceContext`.
- Update `JiraCollector` to set `source = JIRA` and populate `sourceContext` with the project key.
- Update `GitHubIssuesCollector` to set `source = GITHUB`.

**Metric engine:** `calcDailyIssuesCreated`, `calcDailyIssuesClosed`, and
`calcIssueLeadTime` call through `IssueRepository`; once the repository queries include
the UNION branch, these metrics will automatically reflect Jira issues for mapped projects
with no changes needed in `MetricsService` itself.

**Thesis §8.7:** The pre-fix behaviour (Jira issues contributing zero) must be documented as
a worked example of a silent-zero validity threat — see T6.3.

## Alternatives Considered

**Option A — drop Jira from metrics.** Rejected: Jira data would have no metric value,
undermining the thesis claim that the platform is cross-source.

**Option B — first-class source type.** Rejected: requires parallel query variants, new
metric types, and frontend widget changes disproportionate to the thesis scope. Also
produces split series rather than a unified project view, which weakens the dashboard UX.
