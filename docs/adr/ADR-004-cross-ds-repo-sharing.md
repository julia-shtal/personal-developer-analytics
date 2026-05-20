# ADR-004 — Cross-Datasource Repository Sharing Strategy

## Status

Accepted

## Context

`git_repositories` has a global `UNIQUE` constraint on `repo_full_name` (`owner/repo` string).
This means the same upstream repository can only have **one canonical row** in the system,
regardless of how many users or datasources might want to track it.

The current workaround is the `user_repo_registrations` subscription table: a second user
(or the same user with a second GitHub datasource) who wants to track a repo that is already
registered attaches via a `UserRepoRegistration` row rather than inserting a second
`git_repositories` row. The canonical row — and therefore the collection credentials — stays
with the datasource that first registered the repo.

This model has two observable properties:
1. **No duplicate commit ingestion.** A commit with a given SHA is stored once
   (`git_commits.hash UNIQUE`). Metrics for User B derived from a shared repo use the same
   commit rows as User A.
2. **Collection ownership is implicit.** Whoever owns the canonical `git_repositories` row
   controls the sync schedule. Subscribers get reads for free; they do not trigger independent
   collection.

The question this ADR answers: should we keep this model, allow independent rows per datasource,
or introduce an explicit attachment join table?

## Decision

**Option B — Status quo.** Keep the global `UNIQUE` constraint on `repo_full_name` and formalise
the subscription model as the canonical share mechanism.

Rationale:
- The constraint prevents duplicate commit rows and the metric double-counting that would follow.
  Lifting it would require deduplication logic throughout the metric engine — a disproportionate
  cost for the thesis timeline.
- The subscription table (`user_repo_registrations`) already exists and has been in use since V1.
  T2.2 elevated it to a first-class API (`POST /datasources/{id}/repos` subscribe branch).
- The `T4.3` access view (`user_accessible_repos`) will query subscriptions alongside direct
  ownership, making the sharing transparent to callers without changing the storage model.
- Storage duplication is zero: all users sharing a repo read the same commit rows.

## Consequences

**T3.1 (this task):** No migration required. Add an explanatory comment to
`GitRepositoryEntity.repoFullName` and document the model in `PROJECT_DESCRIPTION.md` §3.8.

**T4.3 (`user_accessible_repos` view):** The view must union three paths — direct ownership,
`UserRepoRegistration` subscriptions, and team membership — to return all repos a user can
query. The UNIQUE constraint means the canonical row appears at most once per repo in the result,
regardless of how many users subscribe to it.

**Metric engine:** `repoIds` collected by `RepoService.listAccessible(userId)` may include repos
the user subscribes to but does not own. All metric queries already accept a `repoIds` list and
attribute by `author_email` / `githubLogin`, so shared repos do not pollute individual metrics.
One canonical commit row per SHA means aggregates over the shared repo are consistent across all
subscribers.

**Attach API (T2.2):** `DataSourceService.attachRepo` implements a three-branch logic:
1. Canonical row does not exist → create it under the caller's datasource.
2. Canonical row exists under the same datasource → idempotent return.
3. Canonical row exists under a different datasource → create a `UserRepoRegistration` only;
   the canonical row and its commits are not touched.

Detach mirrors this: removing the canonical row is blocked (409) if other subscriptions exist.

## Alternatives Considered

**Option A — Independent rows per datasource.**
`ALTER TABLE git_repositories DROP CONSTRAINT ...; ADD CONSTRAINT uq_repo_per_ds UNIQUE (data_source_id, repo_full_name);`
Each datasource gets its own `git_repositories` row and collects commits independently. Rejected
because the same commit SHA would be ingested N times (once per subscribing datasource), and the
`git_commits.hash UNIQUE` constraint would need to be relaxed or scoped per datasource — doubling
storage and complicating every metric query that joins commits to repos.

**Option C — Canonical row + explicit attachment join table (`data_source_repo_attachments`).**
Move `collect_issues`, `last_scan_at`, `last_fetched_commit_hash` to an attachment table keyed
`(data_source_id, repository_id)`. Collectors read sync state from the attachment, not the
repository entity. Rejected for this project because the effort (+2 days, per the task estimate)
exceeds the architectural benefit given that the thesis scope covers a single-user / small-team
scenario. Noted as the preferred path if the system grows to support many teams sharing repos
with independent sync schedules.
