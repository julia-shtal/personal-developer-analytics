# Lessons

Running log of mistakes Claude (or I) made that are worth preventing next time. Promote frequent ones into `CLAUDE.md`; retire stale ones.

## How to use this file

**When to add an entry:** Right after I correct Claude on something. Not after the first time it happens — the first time is just a mistake. Add it the *second* time, or when I can tell I'll see this exact bug again on a different metric / endpoint / migration.

**Format (one line):**

```
- [YYYY-MM-DD] <what went wrong> → <what should happen instead>. (×N)
```

The `×N` counter increments each time I observe the same mistake. No need to add a new line — just bump the count and update the date.

**Promotion rule:** When a lesson hits `×3`, move it up into `CLAUDE.md` under the appropriate section. The lesson stays here too, but marked `→ PROMOTED [date]` so I have the history.

**Retirement rule:** Lessons that haven't fired in 30+ days get deleted. If the rule still matters, it should already be in `CLAUDE.md` or in a SKILL.md.

**Git policy:** This file is checked in. It's a thesis-quality artifact — the methodology section can reference "AI-assisted development with a running correction log" and point here.

---

## Categories

Pre-seeded with the mistakes most likely on this codebase, based on architecture review. Marked `[seed]` until observed in real use — delete a seed entry the first time you'd otherwise add it organically.

### Metrics calculation

- `[seed]` Bypassed `MetricsService.saveMetric(...)` and called `metricSnapshotRepository.save(...)` directly → must go through `saveMetric` for the `findExisting` upsert guard, or duplicate snapshots accumulate. (×0)
- `[seed]` Filtered commits by `User.githubLogin` instead of `User.email` → commits attribute via email; only PRs/reviews attribute via githubLogin. (×0)
- `[seed]` New metric calc forgot the `team` parameter and saved with `team = null` always → personal-only metrics break team-scope queries silently. (×0)
- `[seed]` Computed a ratio without guarding zero denominator → return null (not 0) when sample size is 0, so the AI layer's `anomaly` detection ignores it. (×0)

### Ingestion & async

- `[seed]` Added a synchronous GitHub API call inside a `@RestController` method → must go through the two-phase pattern: fast ingest with `stats_status='PENDING'`, then scheduler enriches. (×0)
- `[seed]` Forgot to set `stats_attempts` or `stats_fetched_at` on enrichment failure → enrichment scheduler can loop forever on the same broken row. (×0)

### Security & RBAC

- `[seed]` Added a new metric endpoint without `@PreAuthorize` → path-level rule is a backstop, not a substitute; method-level is required. (×0)
- `[seed]` Trusted a `userId` from the request body / path variable to scope a query → use `CheckHelper.currentUser()` for the authenticated identity. Path/body userIds are only allowed for MANAGER/ADMIN endpoints after explicit authorization checks. (×0)
- `[seed]` Added a new JWT claim in `JwtService.generateAccessToken` but didn't propagate validation to `JwtAuthFilter` → claim is issued but never checked. (×0)

### Schema & migrations

- `[seed]` Edited an entity field type without writing a new Flyway migration → `ddl-auto: validate` will fail on next startup. (×0)
- `[seed]` Edited a committed `V<N>__*.sql` file instead of writing `V<N+1>__*.sql` → Flyway checksum mismatch on every other machine. (×0)
- `[seed]` Added a new FK without `ON DELETE CASCADE` (or `SET NULL` where appropriate) → V22 set the convention; new FKs in the data ingestion chain must follow it. (×0)

### AI layer

- `[seed]` Added a new metric type to the AI context list in `MetricsAiService` but didn't bump the cache key or evict `ai_summaries` → stale entries return summaries missing the new metric. (×0)
- `[seed]` Modified the system prompt without keeping the "return JSON only, no markdown" instruction → response parser breaks because it expects raw JSON. (×0)
- `[seed]` Changed the `AggregatedMetricsContext` shape without updating the prompt's documented field list → model returns inconsistent JSON shape. (×0)

### Frontend

- `[seed]` Added a metric endpoint and a new chart, but didn't invalidate the React Query cache on the recalculate mutation → user sees stale numbers until staleTime (2 min) expires. (×0)
- `[seed]` Hardcoded a hex color in a new widget instead of using the existing Tailwind palette → breaks dark mode and inconsistent with other widgets. (×0)
- `[seed]` Created a `<form>` element with native submit → use button `onClick` handlers; the auth interceptor needs explicit Axios calls. (×0)

### Thesis terminology

- `[seed]` Reworded a metric description in code/UI without updating `docs/metrics/<slug>.md` first → thesis chapter and code drift apart; examiners notice. The doc is the source of truth, not the code comments. (×0)
- `[seed]` Used "average lead time" in a UI label when the metric is actually `..._MEDIAN` → label must match the metric type suffix. (×0)

### Tooling & environment

- `[seed]` Used `npm run` from the project root expecting it to find frontend scripts → must `cd frontend && npm run ...`. (×0)
- `[seed]` Forgot `docker-compose up -d` before `./mvnw spring-boot:run` → Hibernate can't connect to Postgres on :5432. (×0)

---

## Promoted (historical)

When a lesson graduates to `CLAUDE.md`, move its line here with the promotion date. Keep the history — useful for the thesis methodology section.

*(none yet)*

---

## Retired (historical)

Lessons that haven't fired in 30+ days, captured here for posterity before deletion. Useful if a regression brings the mistake back.

*(none yet)*
