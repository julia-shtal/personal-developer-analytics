# Improvement Tasks — Technical Specification

**Repository:** `https://github.com/julia-shtal/personal-developer-analytics`
**Base branch:** `dev` (HEAD `f49fe32`)
**Source:** `IMPROVEMENT_IDEAS.md` — turned into Claude Code tasks here, **plus a full in-app messaging system** (which supersedes the `team-messaging` mailto stand-in from `FOLLOWUP_TASKS.md` T10).
**Ticket numbering:** continues from the follow-up backlog — starts at **PDA-54**.

---

## Migration Numbering

Several tasks add Flyway migrations. Use the **next free number** at implementation time — confirm with:
```
ls dev-analytics/src/main/resources/db/migration/ | sort -V | tail -3
```
The document references migrations as `VNN__name.sql`; replace `NN` with the next integer in sequence. Apply them in PR order.

## Conventions (every task)

- **Stack pin:** React 19, Vite 5, Tailwind v4, TypeScript strict, TanStack React Query 5; Spring Boot 3.5.10, Java 17.
- **Flyway:** versioned, idempotent, `-- rollback` block at the end.
- **Tests:** Vitest + Testing Library for new frontend components; `@SpringBootTest` slice + service unit tests for backend.
- **Secrets:** never log tokens or secrets. Encryption keys come from env vars, never committed.
- **PROJECT_DESCRIPTION.md:** update §3 (backend), §5 (REST API), §10 (frontend) for each PR that changes a surface.

---

# PR1 — PDA-54 — Security Hardening

**Branch:** `PDA-54-security-hardening`
**Covers:** Ideas 1.1, 1.2, 1.7, 1.9
**Backend:** yes (encryption migration + rate limiting). **Frontend:** small (single-flight refresh).

## T1 — Real AES-GCM token encryption + separate key

**Idea 1.1 + 1.2** · **Files:** `SimpleTokenEncryptor.java` (replace), `application.yml`, new Flyway migration · **Effort:** M

**Goal**
Replace the fake base64 "encryption" with authenticated AES-256-GCM, using a dedicated key separate from the JWT signing secret. Re-encrypt any tokens already stored under the old scheme.

**In scope**
- New `AesGcmTokenEncryptor` (rename or replace `SimpleTokenEncryptor`, keeping the same `encrypt`/`decrypt` interface so the 4 call sites don't change): `JiraProjectService`, `JiraCollector`, `DataSourceService`, `GitHubClientFactory`.
  - AES-256-GCM, 96-bit random IV per value, IV prepended to ciphertext, whole thing base64-encoded. Authentication tag verified on decrypt.
  - Key from new config `app.encryption.key` (env `ENCRYPTION_KEY`), a 32-byte base64 value — **separate** from `app.jwt.secret`.
- Add `app.encryption.key` to `application.yml` with an env placeholder; document generating it (`openssl rand -base64 32`).
- **Migration of existing data:** the old format is `base64(jwtSecret + ":" + token)`. Provide a one-time `@Component` runner (`TokenReEncryptionRunner implements ApplicationRunner`, guarded by a feature flag `app.encryption.migrate-on-startup=false` default) that:
  1. Reads every `data_source_config.api_token_encrypted`.
  2. Detects old-format values (decode base64, check for the `jwtSecret + ":"` prefix).
  3. Re-encrypts under AES-GCM and saves.
  4. Logs counts (migrated / skipped / failed) — never logs token values.
  - This runner is idempotent: AES-GCM values won't match the old prefix, so they're skipped.
- Backward-compatible `decrypt`: if a value is old-format, decrypt with the legacy method (so nothing breaks before the migration runs).

**Out of scope**
- Key rotation tooling. Single key for now; note rotation as a future task.

**Acceptance criteria**
- [ ] New tokens are stored as AES-GCM ciphertext (not decodable to plaintext without the key).
- [ ] Tampering with one ciphertext byte makes `decrypt` throw (GCM auth tag works).
- [ ] The re-encryption runner converts old-format rows when enabled, and is a no-op on a second run.
- [ ] All 4 call sites work unchanged.
- [ ] Unit tests: round-trip encrypt/decrypt; tamper detection; legacy-format decrypt; runner idempotency.

---

## T2 — Single-flight token refresh

**Idea 1.7** · **File:** `frontend/src/lib/api.ts` · **Effort:** S

**Goal**
When several requests 401 at once, refresh exactly once and queue the rest behind it.

**In scope**
- In `api.ts`, add a module-level `let refreshPromise: Promise<string> | null`.
- On 401: if `refreshPromise` is null, start the refresh and store the promise; otherwise await the in-flight one. All queued requests retry with the new token once it resolves. Clear `refreshPromise` in `finally`.
- Preserve existing behaviour: no refresh token → clear + redirect to `/login`; refresh failure → clear + redirect.

**Acceptance criteria**
- [ ] Firing 5 concurrent requests that all 401 triggers exactly one `POST /api/auth/refresh` (assert with a mocked adapter).
- [ ] All 5 retried requests use the new access token.
- [ ] Refresh failure still redirects to `/login`.

---

## T3 — Rate limiting on expensive endpoints

**Idea 1.9** · **Files:** new `RateLimitFilter` or interceptor, `pom.xml`, `SecurityConfig.java` · **Effort:** M

**Goal**
Throttle per-user request rates, with a tighter bucket on AI endpoints (each triggers an Ollama call).

**In scope**
- Add Bucket4j (`com.bucket4j:bucket4j-core`) to `pom.xml`.
- A `HandlerInterceptor` (or servlet filter) keyed by authenticated user id:
  - Default bucket: e.g. 120 requests/minute per user.
  - AI bucket (paths under `/api/ai/`): e.g. 10 requests/minute per user.
  - On exhaustion: respond `429 Too Many Requests` with a `Retry-After` header. Route through `GlobalExceptionHandler` for a consistent error body.
- Buckets held in an in-memory `ConcurrentHashMap<Long, Bucket>` (note: per-instance; fine for the current single-instance deployment — mention Redis-backed Bucket4j as the multi-instance upgrade).
- Make limits configurable in `application.yml` under `app.rate-limit.*`.

**Acceptance criteria**
- [ ] Exceeding the AI bucket returns 429 with `Retry-After`.
- [ ] Normal usage is unaffected.
- [ ] Limits are configurable; a test sets a low limit and asserts the 429.

---

## PR1 Summary
- [ ] Tokens are AES-GCM encrypted with a dedicated key; existing data migrated.
- [ ] Concurrent 401s cause one refresh.
- [ ] Expensive endpoints are rate-limited.
- [ ] `mvn -B test`, `npm run build/lint/test:run` pass. PROJECT_DESCRIPTION.md §3 updated.

---

# PR2 — PDA-55 — Refresh Token in httpOnly Cookie

**Branch:** `PDA-55-httponly-refresh`
**Covers:** Idea 1.6 · **Backend + Frontend** · **Effort:** L

**Note:** This is the largest security change and touches the whole auth flow, so it's isolated in its own PR. It can be deferred if the localStorage tradeoff is accepted — document the decision either way.

**Goal**
Move the refresh token out of `localStorage` (XSS-readable) into an httpOnly, Secure, SameSite cookie. Keep the short-lived access token in memory (not localStorage).

**In scope**
- **Backend:** on login/refresh, set the refresh token as an httpOnly `Set-Cookie` (`Secure`, `SameSite=Strict`, path `/api/auth/refresh`). `POST /api/auth/refresh` reads the cookie instead of the request body. Logout clears the cookie. CORS config must allow credentials.
- **Frontend:** stop persisting the refresh token. Keep the access token in a module variable (memory). `api.ts` sends `withCredentials: true` so the cookie rides along on refresh. On app load, attempt a silent refresh to repopulate the access token.
- Update `AuthContext` to drop refresh-token localStorage usage.

**Out of scope**
- Access token also to cookie (keep it in memory + Authorization header).

**Acceptance criteria**
- [ ] Refresh token no longer appears in `localStorage` or JS-readable storage.
- [ ] Reloading the page silently restores a session via the cookie.
- [ ] Login, refresh, logout all work; CORS with credentials works.

---

# PR3 — PDA-56 — Data Reliability (Sync State, Backfill, Freshness)

**Branch:** `PDA-56-data-reliability`
**Covers:** Ideas 1.3, 1.4, 2.9 · **Backend + Frontend**

## T4 — Persist sync job state

**Idea 1.3** · **Files:** new `sync_jobs` table + entity/repo, `SyncJobTracker.java` · **Effort:** M

**Goal**
Survive restarts and stop orphaning "syncing…" UI. Persist a durable last-known status per job alongside the in-memory live tracker.

**In scope**
- Flyway `VNN__sync_jobs.sql`: `sync_jobs(id, data_source_id FK, status, phase, total_processed, started_at, completed_at, result, error)`.
- On job start/phase-change/completion, `SyncJobTracker` also writes to `sync_jobs` (the in-memory map stays as the fast live view; the table is the durable record).
- On startup, mark any job left in `running` state as `interrupted` (it can't still be running after a restart).
- Sync-status endpoint falls back to the persisted record when the in-memory entry is gone.

**Acceptance criteria**
- [ ] After a restart mid-sync, the job shows `interrupted`, not a perpetual "syncing".
- [ ] Completed jobs survive restart and are queryable.

## T5 — Metrics backfill (gap detection)

**Idea 1.4** · **Files:** `MetricsScheduler.java`, `MetricsService.java` · **Effort:** S

**Goal**
The nightly job should compute every missing day since the last successful run, not just yesterday.

**In scope**
- Track the last computed metrics date per user (derive from the latest `metric_snapshots.date`, or a small `last_metrics_date` column).
- On the nightly run, for each user compute from `lastComputed + 1` through `yesterday` (cap the window, e.g. max 30 days, to avoid runaway backfills; log if capped).
- Add a manual `POST /api/metrics/backfill?from=&to=` (self-scoped) so a user can trigger a backfill for a range after connecting a new source.

**Acceptance criteria**
- [ ] Simulating a 3-day downtime then running the scheduler fills all 3 missing days.
- [ ] Backfill window cap is enforced and logged.
- [ ] Manual backfill endpoint works and is self-scoped.

## T6 — Data freshness indicators

**Idea 2.9** · **Files:** datasource status DTO, `StatusBar.tsx`, KPI tiles · **Effort:** S

**Goal**
Show when data was last updated, per source and on the dashboard.

**In scope**
- Expose `lastSuccessfulSyncAt` per data source (likely already available) and a dashboard-level `metricsComputedThrough` date.
- Frontend: a subtle "updated {timeAgo}" line on each data source card; the StatusBar "last sync" wired to real values; an optional freshness chip on the dashboard hero.

**Acceptance criteria**
- [ ] Each data source shows its last successful sync time.
- [ ] Dashboard shows the date metrics are current through.

---

# PR4 — PDA-57 — Core Metric Tests + Observability

**Branch:** `PDA-57-metric-tests-observability`
**Covers:** Ideas 3.1, 3.2 · **Backend**

## T7 — Unit tests for metric calculations

**Idea 3.1** · **Files:** new tests under `src/test/.../metrics/` · **Effort:** L

**Goal**
The 19 `calc*` methods in `MetricsService` are the analytical core and have no direct unit tests. Cover them with focused, deterministic tests over fixed input fixtures.

**In scope**
- For each metric, a test with a small hand-built dataset and an asserted exact output:
  - daily commits / avg size, PR created/merged, issues created/closed, churn ratio
  - PR lead-time median, issue lead-time median, first-commit-to-merge median, review-response median
  - focus ratio, after-hours ratio (incl. timezone correctness — a commit at 20:00 local counts as after-hours), refactor ratio
  - deep-work streak (consecutive-day logic, including gaps), merge-to-main frequency/week
  - knowledge-silo score, PR-size complexity, merge-without-review ratio
  - `medianOfLongs` edge cases (empty, single, even/odd counts)
- Cover edge cases: empty input, single data point, all-zero, timezone boundaries.
- Refactor `private` calc methods to package-private only if needed for testing — prefer testing through `calculateDailyMetrics` with fixtures where practical.

**Acceptance criteria**
- [ ] Every metric type has at least one test asserting an exact computed value.
- [ ] After-hours and deep-work-streak have explicit timezone / gap edge-case tests.
- [ ] `mvn -B test` green; coverage of `MetricsService` meaningfully increased.

## T8 — Spring Actuator health + info

**Idea 3.2** · **Files:** `pom.xml`, `application.yml`, `SecurityConfig.java` · **Effort:** S

**Goal**
A real health endpoint, also usable to back the StatusBar "online" indicator.

**In scope**
- Add `spring-boot-starter-actuator`. Expose `/actuator/health` (and `/actuator/info`); keep others disabled.
- Custom health indicators: database (built-in) + Ollama reachability (ping the base URL).
- Permit `/actuator/health` unauthenticated in `SecurityConfig`; keep the rest authenticated/admin.
- Frontend (optional): StatusBar polls `/actuator/health` for the live/online dot.

**Acceptance criteria**
- [ ] `/actuator/health` returns component status incl. db and Ollama.
- [ ] Endpoint is reachable without auth; other actuator endpoints are not exposed.

---

# PR5 — PDA-58 — Notification Delivery + Anomaly Surfacing

**Branch:** `PDA-58-notifications-anomaly`
**Covers:** Ideas 1.5, 2.3, 2.4 · **Backend + Frontend**

## T9 — Wire notification preferences to real delivery

**Ideas 1.5 + 2.3** · **Files:** new `NotificationDispatchService`, scheduler, `EmailService` · **Effort:** M

**Goal**
Make the four existing notification toggles actually do something: weekly AI-brief email + sync-failure + anomaly emails, gated by each user's prefs.

**In scope**
- `NotificationDispatchService` that, for a given user and event, checks `UserNotificationPrefsService` before sending.
- Extend `EmailService` with templated emails (weekly brief, sync failure, anomaly alert, new team member). Plain-text or simple HTML.
- Hook points:
  - Weekly brief: in the existing Monday summary scheduler, after a summary is generated, email users with `aiBrief = true`.
  - Sync failure: on a failed collection job, email users with `syncFailures = true`.
  - Anomaly: when T10 detects anomalies, email users with `afterHours`/relevant pref on.
  - New team member: when `TeamService.addMember` succeeds, email the manager if `newTeamMember = true`.
- All sends are best-effort and logged; a mail failure never breaks the triggering operation.

**Acceptance criteria**
- [ ] A user with `aiBrief = false` receives no weekly brief; `true` does.
- [ ] Sync failure and new-member emails respect their prefs.
- [ ] Mail send failure is caught and logged, doesn't break the caller.
- [ ] Tests assert the pref gate (mock `EmailService`, verify send / no-send).

## T10 — Surface anomalies in the UI

**Idea 2.4** · **Files:** metrics summary/snapshot DTOs, dashboard · **Effort:** S

**Goal**
The `anomaly` flag is already computed but only fed to the AI. Surface it directly.

**In scope**
- Expose per-metric anomaly flags in the dashboard metrics response (compute the same >2σ check the AI context uses, or reuse it).
- Frontend: an anomaly badge on affected KPI tiles + a small "what changed" strip listing anomalous metrics for the period.

**Acceptance criteria**
- [ ] KPI tiles with an anomaly show a badge.
- [ ] The "what changed" strip lists the anomalous metrics, or is hidden when none.

---

# PR6 — PDA-59 — Period-over-Period Comparison + Goals

**Branch:** `PDA-59-comparison-goals`
**Covers:** Ideas 2.1, 2.2 · **Backend + Frontend** · highest product value

## T11 — Period-over-period comparison

**Idea 2.1** · **Files:** metrics endpoint, dashboard, charts · **Effort:** L

**Goal**
Make the "vs last period" story real and verifiable, not just an AI sentence.

**In scope**
- Backend: a metrics endpoint variant that returns the current window **and** the immediately preceding window of equal length, per metric (value + delta + pct change).
- Frontend:
  - KPI tiles show a delta chip (▲/▼ + pct) vs the previous period (the design already hints at "+24% vs prev").
  - A toggle to overlay the previous period as a faint line on the commit/PR charts.
- Reuse `DateRangeContext`; the previous window is derived (same length, immediately before `from`).

**Acceptance criteria**
- [ ] Each KPI shows a correct delta vs the previous equal-length period.
- [ ] Chart overlay toggle shows the previous-period series.
- [ ] Backend delta math has unit tests.

## T12 — Goals & targets

**Idea 2.2** · **Files:** new `metric_goals` table + entity/repo/service/controller, Settings or dashboard UI · **Effort:** L

**Goal**
Let a user set a target per metric and track progress.

**In scope**
- Flyway `VNN__metric_goals.sql`: `metric_goals(id, user_id FK, metric_type, target_value, direction, created_at)` where `direction ∈ {ABOVE, BELOW}` (e.g. lead time should be BELOW target, deep-work streak ABOVE).
- CRUD endpoints `GET/POST/PUT/DELETE /api/goals` (self-scoped).
- Frontend: a "goals" section (Settings or a dashboard panel) to set/edit targets; KPI tiles show progress toward goal (e.g. a small bar or "82% to goal" / "on track" / "off track" chip).

**Acceptance criteria**
- [ ] A user can set, edit, and delete a goal per metric.
- [ ] KPI tiles with a goal show progress and on/off-track state respecting `direction`.
- [ ] Goals are strictly per-user.

---

# PR7 — PDA-60 — In-App Messaging System

**Branch:** `PDA-60-messaging`
**New feature** (supersedes the `team-messaging` mailto stand-in from FOLLOWUP T10) · **Backend + Frontend** · **Effort:** L (largest feature PR)

**Goal**
A real 1:1 direct-messaging system between users who share a team. Replaces the mailto link with an in-app inbox, threads, compose, unread badges, and (optional) notification integration.

**Scope decision:** 1:1 direct messages only (no group threads in v1). Users may only message people they share at least one team with — keeps it privacy-respecting and bounded. Delivery is via polling in v1; WebSocket/SSE is noted as a future upgrade.

## B-MSG — Schema + domain

**Effort:** M

- Flyway `VNN__messages.sql`:
  ```sql
  CREATE TABLE messages (
      id           BIGSERIAL PRIMARY KEY,
      sender_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      recipient_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      body         TEXT   NOT NULL,
      created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
      read_at      TIMESTAMP
  );
  CREATE INDEX ix_messages_pair    ON messages (sender_id, recipient_id, created_at);
  CREATE INDEX ix_messages_inbox   ON messages (recipient_id, read_at);
  -- rollback: DROP TABLE messages;
  ```
- `MessageEntity`, `MessageRepository` with queries for: conversation between two users (ordered), inbox conversation list (latest message per counterpart), unread count.

## T13 — Messaging API

**Effort:** M · **Depends on:** B-MSG

- `MessageService`:
  - `send(sender, recipientId, body)` — validates the two users **share a team** (else `ForbiddenException`); trims/limits body length; saves.
  - `conversation(me, otherUserId, pageable)` — messages between the two, newest-first, paginated. Marks the other user's messages to me as read.
  - `inbox(me)` — list of conversations: counterpart + last message + unread count.
  - `unreadCount(me)` — total unread.
- `MessageController` under `/api/messages`:
  - `POST /api/messages` `{ recipientId, body }`
  - `GET /api/messages/conversations` (inbox)
  - `GET /api/messages/conversations/{userId}?page=&size=`
  - `GET /api/messages/unread-count`
- Tests: send to a non-teammate → 403; send + conversation round-trip; unread count decrements when a conversation is opened.

**Acceptance criteria**
- [ ] Messaging works only between users sharing a team.
- [ ] Opening a conversation marks incoming messages read.
- [ ] Unread count is accurate.

## T14 — Messaging UI

**Effort:** M · **Depends on:** T13

- New `MessagesPage` (route `/messages`) or a slide-out drawer:
  - Left: inbox (conversation list with counterpart avatar, last message preview, unread badge).
  - Right: thread view (message bubbles, sender right / recipient left), compose box at the bottom.
- Sidebar: a "Messages" nav item with an unread-count badge (poll `unread-count` every ~30s via React Query).
- Team member detail modal: the "message" button now opens the thread with that member (replacing the mailto link — remove the `team-messaging` mailto code and its follow-up entry).
- Polling: active conversation refetches every ~10s while open.

**Out of scope**
- Group conversations, attachments, typing indicators, real-time WebSockets (note as future upgrades).

**Acceptance criteria**
- [ ] User can open a conversation from the team member modal and exchange messages.
- [ ] Inbox shows conversations with unread badges; opening clears them.
- [ ] Sidebar shows a live-ish unread count.
- [ ] The old mailto path is removed; `FOLLOWUP_TASKS.md` `team-messaging` is now satisfied — update `docs/REDESIGN_FOLLOWUPS.md`.

**Optional follow-on:** wire a "new message" notification through the T9 dispatch service (gated by a new pref), and consider SSE/WebSocket for true real-time. Leave as `TODO(messaging-realtime):`.

---

# PR8 — PDA-61 — Insight History + Frontend Error Boundary

**Branch:** `PDA-61-history-errorboundary`
**Covers:** Ideas 2.5, 1.8 · **Backend (light) + Frontend**

## T15 — Summary / insight history

**Idea 2.5** · **Files:** AI summary repo/controller, new history view · **Effort:** M
**Depends on:** the AI-summary-persistence task (summaries must be persisted first).

- Backend: `GET /api/ai/summary/history?scope=&limit=` returning past persisted summaries (most recent first) for the current user / a team.
- Frontend: a "history" view — a timeline of past summaries (headline + period + generated date), each expandable to the full summary. Reachable from the AI summary card.

**Acceptance criteria**
- [ ] History lists past summaries newest-first with correct periods/timestamps.
- [ ] Expanding shows the full stored summary.

## T16 — Frontend error boundary

**Idea 1.8** · **Files:** new `ErrorBoundary.tsx`, `main.tsx`/`App.tsx` · **Effort:** S

- A top-level React error boundary with a friendly fallback ("Something went wrong" + reload button), wrapping `<App/>`. Optionally a second boundary around the routed page area so the shell (sidebar/topbar) survives a page-level crash.
- Log the error to the console (and leave a hook for future error reporting).

**Acceptance criteria**
- [ ] A thrown render error shows the fallback instead of a white screen.
- [ ] Reload button recovers.
- [ ] A test renders a throwing child and asserts the fallback appears.

---

# PR9 — PDA-62 — Additional Data Sources

**Branch:** `PDA-62-gitlab-bitbucket`
**Covers:** Idea 2.6 (and lays groundwork for 2.7) · **Backend + Frontend** · **Effort:** L

**Goal**
Add GitLab as a data source (and Bitbucket if time allows), reusing the existing collector/datasource abstraction.

**In scope**
- A GitLab collector implementing the same collection interface used by GitHub (repos, commits, MRs, reviews → mapped to the existing PR/commit model).
- Datasource type + config (base URL for self-hosted GitLab, token, encrypted via the new AES encryptor from PR1).
- Discovery flow parallel to the existing GitHub/Jira discovery modals.
- Frontend: GitLab option in the "connect source" type picker (icon already feasible); reuse the repo-subscribe UI.

**Out of scope**
- Webhooks (separate, see note). Bitbucket optional — ship GitLab first, Bitbucket as a fast-follow if the abstraction holds.

**Acceptance criteria**
- [ ] A user can connect a GitLab source, discover projects, subscribe, and see metrics flow through identically to GitHub.
- [ ] Tokens stored with AES-GCM.

**Future (note, not this PR):** **Webhooks (Idea 2.7)** — receive GitHub/GitLab push/PR/review events for near-real-time updates instead of polling. Larger lift (public endpoint, signature verification, event mapping). Recommend a dedicated PDA ticket after GitLab lands.

---

# PR10 — PDA-63 — Self-Benchmarking

**Branch:** `PDA-63-benchmarking`
**Covers:** Idea 2.8 · **Backend + Frontend** · **Effort:** M

**Goal**
Compare a user's current metrics against their own rolling baseline (privacy-safe — no cross-user data).

**In scope**
- Backend: compute a rolling baseline per metric (e.g. trailing 12-week average/median) and return current-vs-baseline deltas.
- Frontend: a "vs your baseline" indicator on KPI tiles or a dedicated panel ("churn is 18% above your 12-week average").

**Out of scope**
- Cross-user/cohort benchmarking (privacy-sensitive; separate design decision).

**Acceptance criteria**
- [ ] Each metric shows current vs personal rolling baseline.
- [ ] Baseline window is configurable; math is unit-tested.

---

# PR11 — PDA-64 — Project Polish (Docs, Seed Data, Cleanup, A11y, Deployment)

**Branch:** `PDA-64-polish`
**Covers:** Ideas 3.3, 3.4, 3.5, 3.6, 3.7 · mostly non-code

## T17 — README + setup guide (3.3)
- Full README: overview, architecture diagram, prerequisites (Java 17, Node, Postgres, Ollama), env vars (incl. the new `ENCRYPTION_KEY`), run instructions, test instructions, seeding.
- [ ] A new contributor can clone and run from the README alone.

## T18 — Seed / demo data mode (3.4)
- A seed profile/command (`--spring.profiles.active=demo` or a `DataSeeder` runner) that creates a demo user with realistic metrics across a date range, so the full dashboard is visible without connecting a real source.
- [ ] Starting in demo mode shows a fully populated dashboard for a demo login.

## T19 — Language cleanup (3.5)
- Replace Russian comments (e.g. in the old encryptor — now removed in PR1) and any other non-English comments/identifiers with English.
- [ ] No non-English comments remain (`grep` for Cyrillic returns nothing).

## T20 — Accessibility pass (3.6)
- Keyboard-only walkthrough of every flow; aria-labels on icon-only buttons and charts; color-contrast check in both themes; focus order audit.
- [ ] All interactive elements reachable and labelled; contrast meets WCAG AA in light and dark.

## T21 — Deployment (docker-compose) (3.7)
- `docker-compose.yml` bringing up app + Postgres + Ollama; a Dockerfile for the app; documented one-command startup.
- [ ] `docker compose up` brings the full stack online locally.

---

# Cross-Cutting Checklist (every task)

- [ ] No secrets/tokens logged.
- [ ] Migrations versioned + idempotent + `-- rollback`.
- [ ] New backend code has unit + slice tests; new frontend primitives have a smoke test.
- [ ] `import type` for type-only TS imports; no `any`.
- [ ] PROJECT_DESCRIPTION.md updated for changed surfaces.
- [ ] `mvn -B test` and `npm run build/lint/test:run` pass before merge.

---

# Sequencing & Priority

Strict order is not required across all PRs, but this is the recommended sequence (correctness → reliability → value → polish):

1. **PDA-54** Security hardening (encryption, refresh race, rate limiting) — do first.
2. **PDA-55** httpOnly refresh cookie — security, isolated/optional.
3. **PDA-56** Data reliability (sync state, backfill, freshness).
4. **PDA-57** Metric tests + observability — foundational, low risk.
5. **PDA-58** Notification delivery + anomaly surfacing.
6. **PDA-59** Period comparison + goals — highest product value.
7. **PDA-60** Messaging system.
8. **PDA-61** Insight history + error boundary (history depends on AI-summary persistence being done).
9. **PDA-62** GitLab/Bitbucket data sources.
10. **PDA-63** Self-benchmarking.
11. **PDA-64** Polish (README, seed data, cleanup, a11y, docker).

Dependencies worth noting: PR8/T15 (history) needs the AI-summary-persistence task merged first; PR9 token storage relies on PR1's AES encryptor; T10 anomaly emails (PR5) pair with T10 surfacing.
