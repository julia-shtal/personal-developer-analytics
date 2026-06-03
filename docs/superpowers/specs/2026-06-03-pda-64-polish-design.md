# PDA-64 — Project Polish: Design Spec

**Date:** 2026-06-03  
**Branch:** `PDA-62` (current; task runs in the GitLab datasource branch)  
**Covers:** T17 README, T18 Demo data, T19 Language cleanup, T20 Accessibility, T21 Docker  

---

## Scope

Five polish tasks that bring the thesis project to a presentable, reproducible state for examination and demo. No new business features; no new REST endpoints; no Flyway migrations.

---

## T17 — README + Setup Guide

A single `README.md` at the repo root (replacing the current one-liner).

**Sections:**
1. **Overview** — one-paragraph thesis context; what the system collects and visualises.
2. **Architecture** — Spring Boot 3.5 + React 18 SPA + PostgreSQL 16 + Ollama (llama3.2); single-sentence description of each layer.
3. **Prerequisites** — Java 17, Node 20+, PostgreSQL 16 (or Docker), Ollama, `openssl` for key generation.
4. **Environment variables** — table: variable, purpose, default, generation command. Covers `JWT_SECRET`, `ENCRYPTION_KEY`, `SMTP_*`, `OLLAMA_BASE_URL`, `COOKIE_SECURE`, `POSTGRES_PASSWORD`.
5. **Run modes:**
   - Local dev (two terminals): `./mvnw spring-boot:run` + `cd frontend && npm run dev`
   - Fat JAR: `./mvnw clean package` then `java -jar target/dev-analytics-*.jar`
   - Docker Compose: `cp .env.example .env` → edit secrets → `docker compose up -d`
   - Tests: `./mvnw test` / `npm run test:run`
6. **Demo mode** — `--spring.profiles.active=demo`; login `demo@demo.com` / `demo`; note that first boot seeds data on startup.
7. **Ollama first-time setup** — `ollama serve` (or via compose); `ollama pull llama3.2`; note that the first pull takes a few minutes.

**File:** `README.md` (repo root). No new files.

---

## T18 — Demo Data Seeder

**Goal:** `--spring.profiles.active=demo` starts the app and seeds a fully-populated dashboard without any real data source connection.

### Spring profile

- New `application-demo.yml` in `src/main/resources/` — logs a startup banner ("Demo mode active — seeding demo data if needed"), no other config overrides.
- `DataSeeder` is `@Profile("demo")` so it is never instantiated in production.

### `DataSeeder` component

**Location:** `datasource/service/DataSeeder.java`  
**Implements:** `ApplicationRunner` (runs once after context is ready)

**Algorithm (idempotent):**

1. If `demo@demo.com` already exists in `users` → return immediately.
2. Create demo user: email `demo@demo.com`, password `demo` (BCrypt-hashed), role `DEVELOPER`, timezone `Europe/Warsaw`, display name `Demo Developer`.
3. Create teammate stub: email `teammate@demo.com`, password `demo`, role `DEVELOPER`, timezone `Europe/Warsaw`, display name `Demo Teammate`.
4. Create demo team `Demo Team` owned by the demo user (team creator = manager in this codebase); add teammate as a member via `TeamService.addMember`.
5. Create a `DataSourceConfig` of type `GIT_LOCAL` owned by the demo user, name `demo-source`.
6. Create a `GitRepositoryEntity` of type `LOCAL`, name `demo-repo`, `repoFullName = "demo/demo-repo"`, linked to the config above.
7. Register the demo user to the repo via `UserRepoRegistration`.
8. For each of the 84 days ending yesterday (12 weeks):
   - Generate deterministic pseudo-random values per metric type using `(metricType.ordinal() * 10000L + dayIndex)` as seed for `java.util.Random`.
   - Call `MetricsService.saveMetric(...)` for every personal metric type with `teamId = null`.
   - Call `MetricsService.saveMetric(...)` for every team metric type with the demo team id.
   - Value ranges follow the per-metric docs (representative sample):
     - `DAILY_COMMITS`: 0–8 (int)
     - `AVG_COMMIT_SIZE`: 10–400 (lines)
     - `FOCUS_RATIO`: 0.30–0.95 (double)
     - `AFTER_HOURS_RATIO`: 0.0–0.40 (double)
     - `PR_LEAD_TIME_MEDIAN`: 1–72 (hours, stored as double)
     - `DEEP_WORK_STREAK`: 0–7 (days)
     - `CHURN_RATIO`: 0.0–0.50 (double)
     - `PR_CREATED`: 0–3 (int), `PR_MERGED`: 0–3 (int)
     - `ISSUES_CREATED`: 0–4 (int), `ISSUES_CLOSED`: 0–4 (int)
     - `ISSUE_LEAD_TIME_MEDIAN`: 1–168h (double), `FIRST_COMMIT_TO_MERGE_MEDIAN`: 2–96h (double)
     - `REVIEW_RESPONSE_TIME_MEDIAN`: 0.5–24h (double)
     - `REFACTOR_RATIO`: 0.0–0.40 (double)
     - `MERGE_TO_MAIN_FREQUENCY`: 0–5 per week (double)
     - `KNOWLEDGE_SILO_SCORE`: 0.0–1.0 (double)
     - `PR_SIZE_COMPLEXITY`: 1–500 lines (double)
     - `MERGE_WITHOUT_REVIEW_RATIO`: 0.0–0.30 (double)

**No direct SQL.** All writes go through `MetricsService.saveMetric` to honour the upsert guard.

---

## T19 — Language Cleanup

**No Russian comments found** — `grep` for Cyrillic returns nothing. The cleanup is ADR/Ticket references only.

### ADR references → plain English (9 files)

| File | Current ref | Replacement |
|------|-------------|-------------|
| `git/model/GitRepositoryEntity.java` | Class Javadoc ADR-003/004, field comment ADR-003 | Replace class Javadoc with: *"One canonical row per upstream repository. Multiple users access shared repos through UserRepoRegistration rather than duplicate rows."* Field comment: *"Discriminator for repo storage backend."* |
| `git/model/RepoType.java` | `/** Discriminator ... — see ADR-003. */` | `/** Discriminator for repo storage backend (LOCAL, GITHUB, GITLAB). */` |
| `git/model/UserRepoRegistration.java` | Javadoc ADR-004 refs | Replace with: *"Join between a user and a shared repository. Allows multiple users to subscribe to the same GitRepositoryEntity without duplicating it."* |
| `git/service/RepoService.java` | `// See V38 migration and ADR-004` | `// View defined in V38 migration` |
| `issue/model/IssueEntity.java`, `IssueSource.java`, `IssueRepository.java` | ADR-005 refs | Replace with plain description of COALESCE routing: *"Discriminator set on every save path so queries can filter by source without a join."* / *"COALESCE(i.repository_id, rm.repository_id) routes both Jira and GitHub issues to the correct repo."* |
| `jira/model/JiraProjectEntity.java`, `jira/repository/JiraProjectRepository.java` | ADR-002/004 refs | Replace with: *"One canonical row per (baseUrl, projectKey) pair; users subscribe via UserProjectRegistration."* |

### Ticket reference

- `metrics/service/MetricsService.java:585` — remove `(Ticket 4)` from the comment.

### Scope

Pure comment/Javadoc edits. No logic changes, no new files, no migrations.

---

## T20 — Accessibility Pass

Targeted fixes to existing TSX files only. No new components, no route changes.

### Icon-only buttons — `aria-label`

Grep for `<button` elements with no text child and no existing `aria-label`. Add descriptive `aria-label` to each. Expected locations: sidebar collapse toggle, modal close buttons, copy-to-clipboard buttons, theme toggle, notification dismiss.

### Chart accessibility

Wrap each chart component (`MetricLineChart`, `MetricBarChart`, `MultiLineChart`, `Sparkline`) in a `<figure>` element with:
- `aria-label="<metric name> chart"` (prop-driven)
- A visually-hidden `<figcaption>` with a one-line text summary: e.g. *"14 commits over the selected period"* (derived from the data already in props).

Visually-hidden helper: `className="sr-only"` (Tailwind utility — already in the project).

### Navigation landmarks

- Sidebar `<nav>`: add `aria-label="Main navigation"` if missing.
- Top bar: add `role="banner"` if missing.

### Focus management in modals/drawers

For `DateRangeModal`, `AiMetricExplainDrawer`, `FollowUpDrawer`, `SummaryHistoryDrawer`:
- On open: move focus to the first interactive element (`autoFocus` prop or `useEffect` + `ref.current?.focus()`).
- On close: return focus to the trigger element (store trigger ref before open, call `.focus()` in cleanup).

### Color contrast

No code changes needed — existing Tailwind palette meets WCAG AA. Add a one-line comment in `index.css`: `/* Color palette verified WCAG AA contrast compliant */`.

---

## T21 — Deployment

### `Dockerfile` (`dev-analytics/Dockerfile`)

Multi-stage build:

```
Stage 1 — build (maven:3.9-eclipse-temurin-17-alpine):
  COPY pom.xml + .mvn/ + mvnw
  RUN mvn dependency:go-offline   # cache layer
  COPY src/
  RUN ./mvnw clean package -DskipTests
  # Frontend is built into static/ by the Maven package phase (frontend-maven-plugin)

Stage 2 — runtime (eclipse-temurin:17-jre-alpine):
  COPY --from=build target/dev-analytics-*.jar app.jar
  EXPOSE 8080
  ENTRYPOINT ["java", "-jar", "/app.jar"]
```

All runtime config injected via env vars (already the pattern in `application.yml`).

### `docker-compose.yml` (replace existing)

```yaml
services:
  db:
    image: postgres:16-alpine
    environment: POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD (from .env)
    volumes: postgres_data
    healthcheck: pg_isready

  ollama:
    image: ollama/ollama:latest
    volumes: ollama_models:/root/.ollama
    healthcheck: curl -f http://localhost:11434/api/tags
    # Model pull on first boot via entrypoint wrapper or compose profiles

  app:
    build: ./dev-analytics
    ports: "8080:8080"
    depends_on: db (healthy), ollama (healthy)
    environment:
      SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD,
      JWT_SECRET, ENCRYPTION_KEY, OLLAMA_BASE_URL=http://ollama:11434,
      COOKIE_SECURE=true, SMTP_* (optional)

volumes:
  postgres_data:
  ollama_models:
```

**Ollama model pull:** the `ollama` service uses a shell entrypoint that:
1. Starts `ollama serve &` in the background.
2. Waits until `curl -sf http://localhost:11434/api/tags` succeeds (poll loop, 1s sleep).
3. Runs `ollama pull llama3.2`.
4. Calls `wait` to keep the container alive on the background serve process.

This is expressed as `command: ["/bin/sh", "-c", "ollama serve & until curl -sf http://localhost:11434/api/tags; do sleep 1; done && ollama pull llama3.2 && wait"]` in the compose file. The app service's `depends_on: ollama: condition: service_healthy` uses a healthcheck (`curl -f http://localhost:11434/api/tags`) so the app only starts after the model is present.

### `.env.example` (repo root)

Lists every variable the compose file uses, with generation commands:
```
JWT_SECRET=          # openssl rand -hex 32
ENCRYPTION_KEY=      # openssl rand -base64 32
POSTGRES_PASSWORD=   # choose a password
SMTP_HOST=           # optional
...
```

### README integration

`docker compose up -d` as the documented one-command startup. Note: first boot pulls llama3.2 (~2 GB); subsequent boots are fast.

---

## Acceptance Criteria (consolidated)

- [ ] T17: A new contributor can clone and run from the README alone.
- [ ] T18: `--spring.profiles.active=demo` + `demo@demo.com`/`demo` shows a fully populated dashboard; second startup is a no-op.
- [ ] T19: `grep` for ADR-0 and "Ticket [0-9]" in `src/main/java/` returns nothing. All replacements are plain English.
- [ ] T20: All icon-only buttons have `aria-label`; charts have accessible wrappers; modals manage focus correctly.
- [ ] T21: `docker compose up -d` brings the full stack online; app is reachable at `localhost:8080`.
- [ ] `./mvnw test` and `npm run test:run` pass.

---

## Files Changed (expected)

| File | Change |
|------|--------|
| `README.md` | Full rewrite |
| `dev-analytics/Dockerfile` | New |
| `dev-analytics/docker-compose.yml` | Replace |
| `.env.example` | New |
| `dev-analytics/src/main/resources/application-demo.yml` | New |
| `dev-analytics/src/main/java/.../datasource/service/DataSeeder.java` | New |
| `dev-analytics/src/main/java/.../git/model/GitRepositoryEntity.java` | Comment edits |
| `dev-analytics/src/main/java/.../git/model/RepoType.java` | Comment edit |
| `dev-analytics/src/main/java/.../git/model/UserRepoRegistration.java` | Comment edits |
| `dev-analytics/src/main/java/.../git/service/RepoService.java` | Comment edit |
| `dev-analytics/src/main/java/.../issue/model/IssueEntity.java` | Comment edit |
| `dev-analytics/src/main/java/.../issue/model/IssueSource.java` | Comment edit |
| `dev-analytics/src/main/java/.../issue/IssueRepository.java` | Comment edit |
| `dev-analytics/src/main/java/.../jira/model/JiraProjectEntity.java` | Comment edit |
| `dev-analytics/src/main/java/.../jira/repository/JiraProjectRepository.java` | Comment edit |
| `dev-analytics/src/main/java/.../metrics/service/MetricsService.java` | Remove "Ticket 4" ref |
| Several `frontend/src/` TSX files | aria-label, figure wraps, focus management |
| `dev-analytics/frontend/src/index.css` | Contrast comment |
