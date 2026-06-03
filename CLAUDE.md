# Personal Developer Analytics

Master's thesis project: self-hosted platform aggregating developer productivity metrics from local Git, GitHub, and Jira; React SPA dashboard; AI insights via local Ollama (llama3.2). Academic deliverable — **code quality and code-thesis terminology consistency matter more than shipping speed**.

For component-level detail (entities, every service, every endpoint, every migration), see `PROJECT_DESCRIPTION.md`. Don't preload it — read on demand.

## Run it

```bash
docker-compose up -d                # Postgres 16 on :5432
./mvnw spring-boot:run              # Backend on :8080 (embedded Tomcat + SPA)
cd frontend && npm run dev          # Frontend dev server on :5173
./mvnw test                         # All tests
./mvnw clean package                # Fat JAR (frontend builds into static/)
```

Ollama runs separately: `ollama serve`; first time only `ollama pull llama3.2`.
Health: `GET /actuator/health` (no auth).

## Layout

```
src/main/java/com.juliashtal.devanalytics/
├── auth/        ← register, login, JWT issue, refresh rotation, password reset
├── user/        ← profile, RBAC roles, team membership
├── datasource/  ← DataSourceConfig CRUD + async collection orchestration
├── git/         ← JGit local commit collection
├── github/      ← Two-phase GitHub commit + PR collection, stats enrichment
├── issue/       ← Unified Jira + GitHub issue collection
├── metrics/     ← 19-metric calculation engine, snapshot persistence
├── ai/          ← Ollama client, summary generation, weekly scheduler
├── security/    ← JWT filter, JwtService, token encryptor
├── email/       ← SMTP password reset
├── config/      ← Security, Async, Cache, RestTemplate, SpaFallback
└── exception/   ← GlobalExceptionHandler + domain exceptions
src/main/resources/db/migration/   ← Flyway (V1..V30, append-only)
src/main/resources/static/         ← built React SPA (do not edit by hand)
frontend/                          ← React 18 + Vite + TS + Tailwind + Recharts
docs/metrics/                      ← One markdown file per metric (thesis-canonical wording)
tasks/lessons.md                   ← Running list of mistakes-to-prevent
```

## Non-negotiables

These are rules I've watched go wrong. Treat them as hard constraints unless I explicitly override.

**Layering.** Strict Controller → Service → Repository. Controllers never inject repositories. Services may call other services within the same package freely; cross-package service calls go through an interface or stay on the read side.

**RBAC.** Every `@RestController` method that returns data needs `@PreAuthorize`. Path-level rules in `SecurityConfig` are a backstop, not a substitute. Methods scoping by current user use `CheckHelper.currentUser()` — never trust a `userId` from the request body.

**Timestamps.** `Instant` for storage and API boundaries. `LocalDate` for calendar-day windows (`metric_snapshots.date`, `period_from`, `period_to`). Wall-clock interpretation always reads `User.timezone` — the after-hours metric depends on it. Never `LocalDateTime`.

**Schema.** Flyway owns it (`ddl-auto: validate`). Schema changes mean a new `V<N>__<name>.sql`, never an entity-only edit. Migrations are append-only; do not modify a committed V file. If a change needs data backfill, write the backfill into the same migration.

**Metric scope.** Personal metrics: `team_id = NULL`. Team-scoped metrics: `team_id` set. The `(user_id, team_id, ...)` composite index exists for this. New metric calculations mirror how `calcDailyCommits` handles the `team` parameter.

**Metric attribution.** Commits filter by `User.email`; PRs and reviews filter by `User.githubLogin`. Shared repos must never pollute individual metrics. A metric that joins commits and PRs still attributes per-source.

**Saving metrics.** Always go through `MetricsService.saveMetric(...)`. It uses the native-SQL upsert guard (`findExisting` with `IS NOT DISTINCT FROM` for nullable dimensions). Bypassing it produces duplicate `metric_snapshots` rows that aggregate incorrectly and are painful to clean up post-hoc.

**AI summary cache.** Personal key: `(userId, from, to, repoId)`. Team key: `("team", teamId, from, to)`. Cache name: `ai_summaries`. There is no automatic invalidation on underlying metric changes — if you change the prompt, the context builder, or the parsed response shape, also evict the cache (or change the cache key shape so old entries are dead).

**Bot exclusion.** Filter automated accounts (`dependabot[bot]`, `renovate[bot]`, `github-actions[bot]`) in metric formulas, not at ingestion. Ingestion preserves raw signal for audit. Each metric's `docs/metrics/<slug>.md` must document its bot policy explicitly.

**Two-phase async ingestion.** Anything network-bound follows the pattern in `GitHubCommitIngestService` + `GitHubCommitStatsEnrichmentService`: fast ingest writes the minimum row with `stats_status = 'PENDING'`; `CommitStatsEnrichmentScheduler` (or PR equivalent) catches up every 2 minutes; partial index on `WHERE stats_status = 'PENDING'` is the queue. Request handlers must not block on third-party APIs.

**Token-version logout.** `JwtAuthFilter` validates the JWT's `tokenVersion` claim against `User.tokenVersion`. Logout increments the user's version, invalidating every outstanding access token without a blacklist. Adding a JWT claim means changing `JwtService.generateAccessToken` AND `JwtAuthFilter` validation. The version check stays.

## Style

- Lombok throughout: `@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`. Constructor injection via `@RequiredArgsConstructor`, never `@Autowired` on fields.
- Native SQL is allowed where JPQL is awkward (upsert guards, `IS NOT DISTINCT FROM`, partial-index hints). Comment *why* native is needed.
- Swagger annotations on every controller method — they feed the thesis appendix.
- DTOs over entities at the controller boundary. Never expose `User`, `MetricSnapshot`, etc. directly.

## When adding a new metric

Use the `add-metric` skill. It encodes the canonical pipeline (define → schema-or-projection → repo method → service → controller with `@PreAuthorize` → three test layers → frontend wire-up → thesis doc). Don't re-derive the scope/attribution rules inline — they're in the skill.

## When adding a new data source

No skill exists yet. Manually mirror `GitHubCollector` + `GitHubCommitIngestService` + `GitHubCommitStatsEnrichmentService` + `CommitStatsEnrichmentScheduler`. Add the new type to `DataSourceType` enum and to `DataSourceCollectService`'s dispatch.

## When touching the AI layer

`MetricsAiService.generateSummary` runs a fixed pipeline: fetch context metric snapshots → build `AggregatedMetricsContext` with `{min, max, median, total, trendPct, anomaly}` → render system + user prompt → call `LlmClient.complete` → parse JSON, strip markdown fences. The prompt instructs the model to return JSON only — do not relax this without also relaxing the parser. New "context metric types" must be added to the list in `MetricsAiService` AND the model needs to be told about them in the prompt.

## Thesis terminology

`docs/metrics/<slug>.md` is the canonical definition for each of the 19 metric types. The thesis chapter uses those paragraphs verbatim. **Do not silently reword a metric description.** If a definition needs to change: edit the doc first, then propagate to Javadoc, UI labels, and the AI prompt context. Examiners cross-check.

## What's intentionally NOT in this file

- Generic Spring Boot / React / JPA best practices — Claude already has these.
- Conventional Commits guidance — handled by the `commit-commands` plugin.
- Mermaid syntax — handled by `claude-mermaid`.
- Architecture diagram generation — handled by archflow + `docs/architecture/`.
- Per-metric formulas — they live in `docs/metrics/`.
- Per-endpoint contracts — they live in `PROJECT_DESCRIPTION.md` and Swagger.

## Iteration

`tasks/lessons.md` is the holding pen. Every time I have to correct Claude on something more than once in a session, add a one-line lesson there. Weekly: promote the high-frequency lessons up into this file, retire ones that haven't fired in a month. **This file should shrink about as often as it grows.**
