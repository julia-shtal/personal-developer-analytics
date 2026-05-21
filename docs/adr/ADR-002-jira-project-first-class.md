# ADR-002 — Jira Project as a First-Class Entity

## Status

Accepted

## Context

Before T1.1, `data_source_configs.project_key` stored the Jira project key (e.g. `PDA`) directly
on the connection/credential record. This forced a 1:1 relationship between a Jira datasource and
a Jira project: a user tracking two projects on the same Atlassian instance had to create two
`DataSourceConfig` rows with duplicate encrypted API tokens. Updating a credential required
touching every row.

This stands in contrast to the GitHub model: one GitHub `DataSourceConfig` serves multiple
`git_repositories` rows, and users subscribe to individual repos via `user_repo_registrations`.
Jira had no intermediary entity — `project_key` was jammed into the connection config itself,
making the Jira collection path a structural outlier (see `ARCHITECTURE_REVIEW.md` §1).

The absence of an intermediary entity also blocked the subscription access model for Jira. Any
user who did not own the datasource had no object to subscribe to, so Jira issues were a silo
accessible only to the datasource creator.

This ADR is conditional on ADR-005 not selecting Option A (drop Jira from metrics). ADR-005
selected Option C (map Jira projects to Git repositories), so the full entity model is required.

The parallel access model (see `ARCHITECTURE_REVIEW.md` §1) makes the symmetry explicit:

```
GitHub path:  DataSourceConfig → git_repositories (canonical row)
                                → user_repo_registrations (subscription)

Jira path:    DataSourceConfig → jira_projects (canonical row)
                                → user_project_registrations (subscription)
```

Both paths use the datasource as a pure credential store and the intermediary entity as the
subscribable collection target.

## Decision

Introduce `JiraProject` as a first-class entity mirroring `GitRepositoryEntity`, and
`UserProjectRegistration` mirroring `user_repo_registrations`.

**`jira_projects` schema:**

```sql
id              BIGSERIAL PRIMARY KEY
data_source_id  BIGINT NOT NULL REFERENCES data_source_configs ON DELETE CASCADE
project_key     VARCHAR(32)  NOT NULL
project_name    VARCHAR(255)
last_scan_at    TIMESTAMPTZ
created_at      TIMESTAMPTZ  NOT NULL
updated_at      TIMESTAMPTZ  NOT NULL
UNIQUE (data_source_id, project_key)    -- see Consequences for the planned upgrade
```

**`user_project_registrations` schema:**

```sql
id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY
user_id     BIGINT NOT NULL REFERENCES users ON DELETE CASCADE
project_id  BIGINT NOT NULL REFERENCES jira_projects ON DELETE CASCADE
UNIQUE (user_id, project_id)
```

No `data_source_id` column on the registration table — we learned that lesson from
`user_repo_registrations.data_source_id` (Issue #2, T1.3): it is derivable via
`project_id → jira_projects.data_source_id`.

`JiraCollector` is refactored to iterate over `jira_projects` for the datasource rather than
reading `project_key` from `DataSourceConfig`, mirroring how `GitHubCollector` iterates over
`git_repositories`.

The `DataSourceConfig.project_key` column is nullified during backfill and eventually dropped by
T1.4.

## Consequences

**T1.1 (executed this decision):**
- `jira/model/JiraProjectEntity.java`, `jira/model/UserProjectRegistration.java`, and their
  repositories and service exist.
- `JiraCollector` reads from `JiraProjectRepository.findAllByDataSource(cfg)`.
- Backfill migration populated `jira_projects` from `data_source_configs.project_key`.

**Known gap — global uniqueness constraint (resolved by T4.5):**
The initial schema uses `UNIQUE(data_source_id, project_key)` — per-datasource scope. This does
not mirror `git_repositories.repo_full_name UNIQUE` (global scope). As a result, two users
tracking the same `(base_url, project_key)` on different datasources can create two canonical
rows for the same upstream Jira project, unlike the GitHub path where the global UNIQUE prevents
row duplication. T4.5 closes this gap by denormalising `base_url_normalized` onto `jira_projects`
and replacing the per-DS constraint with a global `UNIQUE(base_url_normalized, project_key)`.

**Access model (T4.3 parallel for Jira):**
The `user_accessible_repos` view (T4.3) has no Jira equivalent yet. A future
`user_accessible_projects` view would union direct ownership and subscriptions for Jira projects,
mirroring the GitHub access view. Deferred as a follow-up task.

**Metric engine (ADR-005 + T4.2):**
`IssueRepository` queries now include Jira issues routed via `jira_project_repo_mappings`.
`MetricsService.calcDailyIssues*` and `calcIssueLeadTime` pick these up automatically through
the updated repository layer — no `MetricsService` changes were needed.

**Thesis §3 (System Design):**
The Jira domain section in `PROJECT_DESCRIPTION.md` §3 documents `JiraProjectEntity`,
`UserProjectRegistration`, and their repositories. The content-addressed access model described
in ADR-004 (GitHub) applies symmetrically to the Jira path; §3 should cross-reference both ADRs
when describing the intermediary-entity design pattern.

## Alternatives Considered

**Keep `project_key` on `DataSourceConfig` with a multi-key workaround.**
Parse `project_key` as a comma-separated list to support multiple projects. Rejected: schema
semantics of a single `VARCHAR(32)` column make this fragile; it does not solve the token
duplication problem; and it makes the Jira collection path structurally dissimilar from GitHub.

**Virtual project list (no table, just config).**
Store project keys as a JSON array in a `config` JSONB column on `DataSourceConfig`. Rejected:
no FK integrity, no subscription model, no `last_scan_at` per project, and incompatible with
ADR-005 Option C (mapping projects to repos requires a stable FK target on `jira_projects`).

**Split `jira_projects` by datasource (allow duplicate canonical rows per DS).**
Analogous to ADR-004 Option A for repos — let each datasource own an independent project row
with independent issue ingestion. Rejected for the same reasons as ADR-004 Option A: the same
Jira issue would be ingested N times, producing double-counted metrics. The global-uniqueness
path (T4.5) is the correct extension of the content-addressed model.
