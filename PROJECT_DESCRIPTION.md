# Personal Developer Analytics Platform

## Technical Description

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [System Architecture](#2-system-architecture)
3. [Backend — Domain Packages](#3-backend--domain-packages)
4. [Database Design](#4-database-design)
5. [REST API Reference](#5-rest-api-reference)
6. [Security Architecture](#6-security-architecture)
7. [Data Collection Pipeline](#7-data-collection-pipeline)
8. [Metric Calculation Engine](#8-metric-calculation-engine)
9. [AI Layer](#9-ai-layer)
10. [Frontend Application](#10-frontend-application)
11. [Scheduled Tasks](#11-scheduled-tasks)
12. [Technology Stack](#12-technology-stack)
13. [Configuration & Deployment](#13-configuration--deployment)

---

## 1. Project Overview

**Personal Developer Analytics** is a full-stack web application that aggregates developer productivity metrics from multiple sources — local Git repositories, GitHub, and Jira — and presents them through a React single-page dashboard with AI-generated insights powered by a local Ollama LLM.

### Primary Use Cases

- **Individual developers** — track commit activity, PR lead times, code quality signals, and focus patterns over configurable date ranges.
- **Engineering managers** — view per-member breakdowns across shared repositories, trigger team-scoped metric recalculations, and compare member performance.

### Key Capabilities

| Capability | Details |
|---|---|
| Multi-source collection | Local Git (JGit), GitHub commits + PRs + Issues (Kohsuke), Jira Issues (REST) |
| Two-phase async enrichment | Fast ingest → immediate enrich top 150 → background scheduler for the rest |
| Incremental sync | Resumes from last fetched commit hash; no full re-scans |
| 19 metric types | Daily activity, lead times, churn, focus ratio, after-hours ratio, deep-work streak, knowledge silo, refactor ratio, PR size complexity, merge-without-review, merge frequency |
| Dual-scope metrics | Personal (`team = NULL`) and team-scoped (per-member attribution on shared repos) |
| AI insights | On-demand and weekly scheduled summaries via local Ollama (llama3.2); personal and team scopes; Spring Cache backed |
| Stateless JWT auth | HS256 access tokens (15 min), rotating refresh tokens (7 days), token-version logout invalidation |
| RBAC | DEVELOPER, MANAGER, ADMIN — enforced at path level and method level |
| Scheduled automation | Nightly metric recalculation, weekly AI summaries (Mon 08:00 UTC), background stats enrichment every 2 min, daily token cleanup |
| React SPA | Full dashboard with AI explain drawer, datasource management, team admin, settings, served from embedded Tomcat |

---

## 2. System Architecture

Strict **Controller → Service → Repository** layering. No controller accesses a repository directly.

```
┌─────────────────────────────────────────────────────────────────┐
│                       React 18 SPA (Vite)                       │
│   Dashboard · DataSources · Teams · Admin · Settings · Welcome  │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP/JSON  (Axios + JWT Bearer)
┌────────────────────────────▼────────────────────────────────────┐
│                      Spring Security                            │
│              JwtAuthFilter → SecurityContext                    │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                        Controllers (14)                         │
│  Auth · UserProfile · User · Admin · Team                       │
│  DataSource · GitLocal · Repo · GitHub · GitHubPR              │
│  Issues · Metrics · AiSummary · SpaFallback                     │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                        Services (35+)                           │
│  AuthService · UserService · TeamService                        │
│  DataSourceService · DataSourceCollectService                   │
│  AsyncDataSourceCollectService · SyncJobTracker                 │
│  GitLocalCollector · RepoService · GitRepositoryService         │
│  GitHubClientFactory · GitHubRepositoryService                  │
│  GitHubCollector · GitHubCommitIngestService                    │
│  GitHubCommitStatsEnrichmentService                             │
│  GitHubPrCollector · GitHubPullRequestCollector                 │
│  GitHubPrStatsEnrichmentService · GitHubIssuesCollector         │
│  JiraCollector · IssueService                                   │
│  MetricsService · MetricSnapshotService · MetricsScheduler      │
│  MetricsAiService · OllamaLlmClient                             │
│  JwtService · RefreshTokenService · PasswordResetService        │
│  CustomUserDetailsService · EmailService                        │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                      Repositories (13)                          │
│  Spring Data JPA interfaces over PostgreSQL/Hibernate           │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                        PostgreSQL 16                            │
│            Schema managed by Flyway (43 migrations)             │
└─────────────────────────────────────────────────────────────────┘
```

### Package Map

| Package | Responsibility |
|---|---|
| `auth/` | Registration, login, JWT issuance, refresh token rotation, password reset |
| `user/` | User profile management, RBAC roles, team membership |
| `datasource/` | DataSourceConfig CRUD, async collection orchestration, job progress tracking |
| `git/` | Local Git repo registration, JGit commit collection, repo subscription |
| `github/` | GitHub repo registration, two-phase commit/PR collection, stats enrichment |
| `issue/` | Unified Jira + GitHub issue collection and retrieval |
| `metrics/` | 19-metric calculation engine, snapshot persistence and retrieval |
| `ai/` | LLM context building, Ollama client, personal/team summaries, weekly scheduler |
| `security/` | JWT filter, token service, user details, token encryption |
| `email/` | SMTP password reset email |
| `config/` | SecurityConfig, AsyncConfig, CacheConfig, RestTemplateConfig, SpaFallbackController |
| `exception/` | GlobalExceptionHandler, domain exceptions |

---

## 3. Backend — Domain Packages

### 3.1 Application Entry Point

**`DevAnalyticsApplication`** — `@SpringBootApplication`, `@EnableScheduling`. Standard Spring Boot main class.

---

### 3.2 Config

#### `SecurityConfig` (`@Configuration`, `@EnableWebSecurity`, `@EnableMethodSecurity`)
- **`securityFilterChain`** — Stateless sessions, JWT filter before `UsernamePasswordAuthenticationFilter`, CORS for `localhost:8080` and `localhost:5173`, CSRF disabled.
- Protected paths: `/api/admin/**` → ADMIN; `/api/teams/**` → MANAGER or ADMIN; all other `/api/**` → authenticated.
- Public: `/api/auth/*`, `/actuator/health`, static SPA routes.
- **`passwordEncoder`** — `BCryptPasswordEncoder`.
- **`corsConfigurationSource`** — Allows GET, POST, PUT, DELETE, OPTIONS; exposes Authorization header.

#### `AsyncConfig` (`@Configuration`, `@EnableAsync`)
- **`collectTaskExecutor`** — `ThreadPoolTaskExecutor`: corePoolSize=2, maxPoolSize=4, queueCapacity=20. Used by `@Async("collectTaskExecutor")` on `AsyncDataSourceCollectService`.

#### `CacheConfig` (`@Configuration`, `@EnableCaching`)
- Registers a Caffeine-backed `CacheManager`. Named cache: **`ai_summaries`** — used by `MetricsAiService` to cache AI summary responses keyed by `(userId, from, to, repoId)` or `("team", teamId, from, to)`.

#### `RestTemplateConfig` (`@Configuration`)
- **`restTemplate`** — `RestTemplate` with `MappingJackson2HttpMessageConverter`. Used by Jira collector and Ollama client.

#### `SpaFallbackController` (`@Controller`)
- Maps SPA client-side routes (`/`, `/login`, `/register`, `/dashboard`, `/team`, `/team-manage`, `/datasources`, `/settings`, `/admin`, `/welcome`, `/forgot-password`, `/reset-password`) → `forward:/index.html`. Allows React Router to handle navigation.

---

### 3.3 Auth Domain

#### Entities

**`RefreshToken`** — Table `refresh_tokens`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `user_id` | FK → users CASCADE | |
| `token` | VARCHAR(512) UNIQUE | Opaque random string |
| `expires_at` | TIMESTAMPTZ | 7-day TTL |
| `revoked` | BOOLEAN DEFAULT FALSE | Set on use or logout |
| `replaced_by` | VARCHAR(512) | Token string of successor (audit trail) |
| `created_at` | TIMESTAMPTZ DEFAULT now() | |

**`PasswordResetToken`** — Table `password_reset_tokens`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `user_id` | FK → users CASCADE | |
| `token` | VARCHAR(512) UNIQUE | UUID |
| `expires_at` | TIMESTAMPTZ | 1-hour TTL |
| `used` | BOOLEAN DEFAULT FALSE | Set permanently on first use |
| `created_at` | TIMESTAMPTZ DEFAULT now() | |

#### Repositories

**`RefreshTokenRepository`**
- `findByToken(String)` → `Optional<RefreshToken>`
- `@Query("UPDATE ... SET revoked = true WHERE user.id = :userId")` `revokeAllByUserId(Long)`
- `@Query("DELETE ... WHERE expiresAt < :now")` `deleteExpiredTokens(Instant)`

**`PasswordResetTokenRepository`**
- `findByToken(String)` → `Optional<PasswordResetToken>`
- `@Query("DELETE ... WHERE expiresAt < :now OR used = true")` `deleteExpiredOrUsedTokens(Instant)`

#### Services

**`AuthService`** — Dependencies: `UserRepository`, `PasswordEncoder`, `AuthenticationManager`, `JwtService`, `RefreshTokenService`
- `register(RegisterRequest)` — Validates unique username/email, BCrypt-hashes password, saves `User` with `DEVELOPER` role.
- `login(LoginRequest)` — Delegates to `AuthenticationManager`, generates access token + refresh token, returns `AuthResponse`.
- `refreshToken(String)` — Calls `RefreshTokenService.verifyToken()`, rotates token, issues new access token.
- `logout(String)` — Revokes all user refresh tokens, increments `tokenVersion`.

**`RefreshTokenService`** — Dependencies: `RefreshTokenRepository`; config: `refreshTokenExpirationMs`
- `createRefreshToken(User)` → `RefreshToken` (7-day expiry).
- `rotateToken(RefreshToken)` → new `RefreshToken`; marks old as `revoked = true`, sets `replacedBy`.
- `verifyToken(String)` — Validates not revoked and not expired; if already revoked detects reuse attack → calls `revokeAllUserTokens()`.
- `revokeAllUserTokens(Long userId)` → bulk revoke via JPQL update.

**`PasswordResetService`** — Dependencies: `PasswordResetTokenRepository`, `UserRepository`, `PasswordEncoder`, `EmailService`; config: `tokenExpirationMs`, `baseUrl`
- `initiatePasswordReset(String email)` — Looks up user by email (silent if not found), creates `PasswordResetToken`, calls `EmailService.sendPasswordResetEmail()`.
- `resetPassword(String token, String newPassword)` — Validates token (not expired, not used), BCrypt-hashes new password, marks token `used = true`, revokes all refresh tokens.

**`TokenCleanupScheduler`** — `@Scheduled(cron = "0 0 2 * * ?")` (daily 02:00 UTC)
- `cleanupExpiredTokens()` — Deletes expired refresh tokens and used/expired password-reset tokens.

#### DTOs

| Class | Fields |
|---|---|
| `AuthResponse` | `accessToken`, `refreshToken`, `tokenType` ("Bearer"), `expiresIn` |
| `LoginRequest` | `usernameOrEmail`, `password` |
| `RegisterRequest` | `username`, `email`, `password`, `githubLogin?` |
| `TokenRefreshRequest` | `refreshToken` |
| `PasswordResetRequest` | `email` |
| `PasswordResetConfirmRequest` | `token`, `newPassword` |

#### Controller

**`AuthController`** — `@RestController`, `/api/auth`

| Method | Path | Description |
|---|---|---|
| POST | `/register` | Register new user |
| POST | `/login` | Authenticate → access + refresh tokens |
| POST | `/refresh` | Rotate refresh token |
| POST | `/logout` | Revoke all tokens |
| POST | `/forgot-password` | Initiate password reset email |
| POST | `/reset-password` | Confirm token + set new password |

---

### 3.4 Security Domain

**`JwtAuthFilter`** (`OncePerRequestFilter`) — Extracts `Bearer {token}` from `Authorization` header, validates with `JwtService`, checks `tokenVersion` against `User.tokenVersion` (prevents post-logout use), sets `SecurityContext`. Skips `/api/auth/*`.

**`JwtService`** — Config: `key` (HMAC-SHA256 from `JWT_SECRET`), `accessTokenExpirationMs`
- `generateAccessToken(UserDetails)` — Claims: `type="access"`, `roles`, `tokenVersion`.
- `extractUsername(String)`, `extractTokenVersion(String)`, `isTokenValid(String, UserDetails)`.

**`CustomUserDetailsService`** (`UserDetailsService`) — `loadUserByUsername(String)` — looks up by username or email.

**`CustomUserDetails`** (`UserDetails` wrapper) — Wraps `User`, provides `getUsername()` and `getAuthorities()` from `User.role`.

**`SecurityUtils`** — Static helpers: `getCurrentUserId()`, `getCurrentUserRole()`, `getCurrentUserDetails()`. Throws `ForbiddenException` if unauthenticated.

**`CheckHelper`** (`@Component`) — `currentUser()` → `User` via `SecurityUtils` + `UserRepository`. Used in controllers to resolve the authenticated user entity.

**`SimpleTokenEncryptor`** (`@Component`) — Symmetric encryption of API tokens stored in `DataSourceConfig`. `encrypt(plain)` / `decrypt(encrypted)`.

---

### 3.5 Exception Handling

**`GlobalExceptionHandler`** (`@RestControllerAdvice`) — Maps exceptions to HTTP responses:
- `NoSuchElementException` → 404
- `IllegalArgumentException` → 400
- `GitHubException` → 400
- `ForbiddenException` → 403
- `GeneralException` / uncaught `Exception` → 500

**`ApiError`** — Response body: `timestamp`, `status`, `error`, `message`, `path`.

**Exception classes:** `ForbiddenException`, `GeneralException`, `GitException`, `GitHubException`, `JiraException`.

---

### 3.6 User Domain

#### Entities

**`User`** — Table `users`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `username` | VARCHAR(255) UNIQUE NOT NULL | |
| `email` | VARCHAR(255) UNIQUE NOT NULL | |
| `password_hash` | VARCHAR(255) NOT NULL | BCrypt |
| `timezone` | VARCHAR(64) DEFAULT 'Europe/Berlin' | Used in after-hours metric calculation |
| `role` | VARCHAR(32) DEFAULT 'DEVELOPER' | DEVELOPER / MANAGER / ADMIN |
| `token_version` | INT DEFAULT 0 | Incremented on logout; JWT claim must match |
| `github_login` | VARCHAR(255) | PR/issue attribution by GitHub username |
| `last_active_at` | TIMESTAMP | Updated by `ActivityInterceptor` at most once per 5 min; used for active-24h admin KPI |

**`Role`** (Enum) — `DEVELOPER`, `MANAGER`, `ADMIN`.

**`UserNotificationPrefsEntity`** — Table `user_notification_prefs`

| Column | Type | Notes |
|---|---|---|
| `user_id` | BIGINT PK FK → users CASCADE | Shares PK with `users` via `@MapsId` / `@OneToOne` |
| `ai_brief` | BOOLEAN DEFAULT TRUE | Weekly AI summary email toggle |
| `sync_failures` | BOOLEAN DEFAULT TRUE | Datasource sync error alert toggle |
| `after_hours` | BOOLEAN DEFAULT TRUE | After-hours activity alert toggle |
| `new_team_member` | BOOLEAN DEFAULT FALSE | New team member notification toggle |

Created lazily on first read (`UserNotificationPrefsService.getOrCreate`). Row is shared across sessions; partial updates are not supported (all four fields are replaced on PUT).

**`Team`** — Table `teams`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `name` | VARCHAR(255) | |
| `manager_id` | FK → users CASCADE | |
| `created_at` | TIMESTAMPTZ DEFAULT now() | |
| `members` | ManyToMany via `team_members` | join table: `(team_id, user_id)` PK |

#### Repositories

**`UserRepository`** — `findByUsername(String)`, `findByEmail(String)`, `@Query` `incrementTokenVersion(Long id)`.

**`TeamRepository`** — `findByManagerId(Long)`, `findByMembersId(Long)`, `existsByIdAndMembersId(Long, Long)`.

#### Services

**`UserService`** — `getById(Long)`, `updateProfile(Long, UpdateProfileRequest)`, `updateRole(Long, Role)`, `findAll()`, `search(String q)` (LIKE filter on email/username when q non-blank, falls back to `findAll()` otherwise), `delete(Long)`, `touchLastActive(Long userId)` (native UPDATE sets `last_active_at = NOW()`, called by `ActivityInterceptor` at most once per 5 minutes per user via in-memory debounce).

**`AdminService`** — `activeUsersLast24h()` (counts users with `last_active_at > NOW() - INTERVAL '24 hours'`), `databaseSizeBytes()` (calls `pg_database_size(current_database())`), `aiCallsToday()` (counts `metric_summaries` rows where `DATE(generated_at) = CURRENT_DATE`).

**`UserNotificationPrefsService`** — `getOrCreate(Long userId)` (creates a row with defaults on first call; idempotent), `update(Long userId, NotificationPrefsDto)` (persists all four toggles, returns updated DTO).

**`TeamService`** — `getById(Long)`, `createTeam(String name)`, `getMyTeams()`, `addMember(Long teamId, Long userId)`, `removeMember(Long teamId, Long userId)`, `renameTeam(Long teamId, String name)`, `deleteTeam(Long teamId)`. All write operations verify that the current user is the team manager (or ADMIN). `deleteTeam` additionally checks for attached data sources and throws `ConflictException` (409) if any exist; on success it cascade-deletes `team_members` then the team row.

#### DTOs

`UserSummary` (id, username, email, role), `TeamDto`, `NotificationPrefsDto` (aiBrief, syncFailures, afterHours, newTeamMember — Boolean toggles; maps to `user_notification_prefs`), `UpdateProfileRequest`, `CreateTeamRequest`, `AddTeamMemberRequest`, `RenameTeamRequest`, `UpdateRoleRequest`.

#### Controllers

**`UserProfileController`** — `/api/users/me` — `GET` (own profile), `PUT` (update profile), `PUT /password` (change password), `DELETE` (self-delete — 204; 409 if last admin), `GET /notifications` (notification prefs — creates with defaults on first call), `PUT /notifications` (update notification prefs).

**`UserController`** — `/api/users` — `GET` all users (MANAGER/ADMIN).

**`AdminController`** — `/api/admin` — `GET /stats` (active users 24h, DB size bytes, AI calls today — ADMIN only), `GET /users?q=` (list or search users by email/username), `PUT /users/{id}/role`, `DELETE /users/{id}` (ADMIN only).

**`TeamController`** — `/api/teams` — MANAGER/ADMIN only.

| Method | Path | Status | Description |
|---|---|---|---|
| POST | `/` | 200 | Create team |
| GET | `/` | 200 | List caller's non-archived teams |
| POST | `/{teamId}/members` | 200 | Add member |
| DELETE | `/{teamId}/members/{userId}` | 200 | Remove member |
| PUT | `/{teamId}` | 200 | Rename team |
| DELETE | `/{id}` | 204 | Delete team permanently; 403 if not manager/admin; 409 if data sources attached |
| PATCH | `/{id}/archive` | 200 | Set `archivedAt = now()`; team disappears from list; 403 if not manager/admin |
| PUT | `/{id}/config` | 200 | Update `visibility` + `aiBriefSchedule`; 400 if invalid visibility; 403 if not manager/admin |
| POST | `/{id}/duplicate` | 201 | New team: same members, name + " (copy)", requesting user as manager |

**`TeamExportController`** — `/api/teams` — MANAGER/ADMIN only.

| Method | Path | Query | Response |
|---|---|---|---|
| GET | `/{teamId}/export` | `from`, `to` | `text/csv` stream; columns: `username,metric,value,unit,period_from,period_to`; 403 if not team manager/admin |

---

### 3.7 DataSource Domain

#### Entity

**`DataSourceConfig`** — Table `data_source_configs`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `user_id` | FK → users CASCADE NOT NULL | |
| `team_id` | FK → teams CASCADE | Optional; present = team-scoped |
| `type` | VARCHAR(32) | Enum: GIT_LOCAL, GITHUB, JIRA |
| `name` | VARCHAR(255) | Human-readable label |
| `base_url` | VARCHAR(512) | GITHUB / JIRA only; `chk_remote_baseurl` enforces NOT NULL |
| `path` | VARCHAR(1024) | GIT_LOCAL only; `chk_gitlocal_path` enforces NOT NULL |
| `api_token_encrypted` | TEXT | Encrypted API token |
| `enabled` | BOOLEAN DEFAULT TRUE | |
| `last_success_sync` | TIMESTAMPTZ | Updated after successful collection |
| `created_at` / `updated_at` | TIMESTAMPTZ | `@PrePersist` / `@PreUpdate` |

CHECK constraints (added V35):
- `chk_gitlocal_path`: `type != 'GIT_LOCAL' OR path IS NOT NULL`
- `chk_remote_baseurl`: `type = 'GIT_LOCAL' OR base_url IS NOT NULL`

`project_key` was dropped in V32 (migrated to `jira_projects`). `DataSourceValidator` provides pre-DB fast-fail with friendly messages; the CHECK constraints are the enforcement backstop.

**`DataSourceType`** (Enum) — `GIT_LOCAL`, `GITHUB`, `JIRA`.

#### Repository

**`DataSourceConfigRepository`** — `findAllByUser`, `findAllByUserAndTeamIsNull`, `findByIdAndUser`, `findAllByTeam`, `findAllByUserId(Long)`.

#### Services

**`DataSourceService`** — Creates, reads, updates, deletes datasources. Validates ownership and team membership. Encrypts API tokens. Auto-registers `GitRepositoryEntity` on create for local/GitHub types. For GitHub repos already in the system, subscribes the user to the existing data source (no duplicate created). Sets `canDelete` flag in response DTOs (true for creator or ADMIN only; false for subscriptions).

**`DataSourceValidator`** — `validateCreate(CreateDataSourceRequest)`: checks required fields per type, validates path exists (GIT_LOCAL), validates base URL format (HTTP types).

**`AsyncDataSourceCollectService`** — `@Async("collectTaskExecutor")` wrapper. Calls `DataSourceCollectService.collectForDataSource()` in background thread, updates `SyncJobTracker` on start/complete/fail.

**`DataSourceCollectService`** — Dispatches to collectors based on `DataSourceType`. For GITHUB type it also conditionally collects issues per repo when `repo.isCollectIssues()` is true:
- `GIT_LOCAL` → `GitLocalCollector.collectForRepository()`
- `GITHUB` → Phase 1: `GitHubCollector.collectForRepository()` (commits), Phase 2: `GitHubPrCollector.collectForRepository()` (PRs), Phase 3 (conditional): `GitHubIssuesCollector.collectIssuesForRepo()` if `repo.collectIssues`
- `GITHUB_ISSUES` → `GitHubIssuesCollector.collectIssuesForRepo(cfg, repoFullName)` per repo
- `JIRA` → iterates `JiraProjectService.listTrackedProjects(cfg)`; calls `JiraCollector.collectIssues(project)` per project. Logs a warning if no tracked projects exist.
- Updates `lastSuccessSync` on success.

**`SyncJobTracker`** (`@Component`) — In-memory job state map keyed by datasource ID.
- **`JobState`** fields: `running`, `startedAt`, `phaseNumber`, `totalPhases`, `phase` (name), `phaseStartedAt`, `phaseProcessed` (`AtomicInteger`), `phaseTotal` (estimate), `totalProcessed` (`AtomicInteger`), `completedPhases` (thread-safe list of `PhaseSummary{name, itemsSaved, durationSeconds}`), `completedAt`, `result`, `error`.
- `start(Long)`, `setPhase(JobState, String, int)` (archives previous phase), `addProgress(JobState, int)`, `phaseEtaSeconds(JobState)`, `overallEtaSeconds(JobState)`, `complete(Long, String)`, `fail(Long, String)`.
- `@Scheduled(fixedRate=3_600_000)` `cleanup()` — removes completed jobs older than 1 hour.

#### DTOs (Records)

| Record | Fields |
|---|---|
| `CreateDataSourceRequest` | `type`, `name`, `baseUrl?`, `path?`, `apiToken?`, `teamId?`, `repoFullName?`, `projectKey?` (JIRA workflow only — auto-creates first `jira_projects` row) |
| `UpdateDataSourceRequest` | `name?`, `baseUrl?`, `path?`, `apiToken?`, `enabled?` |
| `DataSourceResponseDto` | `id`, `type`, `name`, `baseUrl`, `path`, `enabled`, `lastSuccessSync`, `createdAt`, `teamId`, `canDelete`, `repoCount` |
| `SyncStatusResponse` | `running`, `phaseNumber`, `totalPhases`, `phase`, `phaseProcessed`, `phaseTotal`, `totalProcessed`, `elapsedSeconds`, `phaseEtaSeconds`, `overallEtaSeconds`, `completedPhases`, `result`, `error` |

#### Controller

**`DataSourceController`** — `/api/datasources`, `@PreAuthorize("isAuthenticated()")`

| Method | Path | Response | Description |
|---|---|---|---|
| POST | `/` | 201 DataSourceResponseDto | Create datasource |
| GET | `/` | List | List user's datasources |
| GET | `/{id}` | DataSourceResponseDto | Get one |
| PUT | `/{id}` | DataSourceResponseDto | Update |
| DELETE | `/{id}` | 204 | Delete |
| POST | `/{id}/collect` | 202 | Trigger async collection |
| GET | `/{id}/collect/status` | SyncStatusResponse | Current job status |
| GET | `/collect/status/active` | Map<id, SyncStatusResponse> | All active jobs |

**`DataSourceRepoController`** — `/api/datasources/{id}/repos`, `@PreAuthorize("isAuthenticated()")`

| Method | Path | Response | Description |
|---|---|---|---|
| GET | `/` | `List<RepoDto>` | List repos attached to this datasource |
| POST | `/` | 201 / 200 RepoDto | Attach a repo (`{ repoFullName, collectIssues }`); 200 if already attached (idempotent), 409 if owned by another DS |
| DELETE | `/{repoId}` | 204 | Detach a repo; 409 if other users are subscribed |

---

### 3.9 Jira Domain (T1.1, ADR-005 option C)

`DataSourceConfig` is a pure credential/connection record. Jira collection targets are managed as first-class `JiraProjectEntity` rows, mirroring `GitRepositoryEntity` for GitHub. Users subscribe to individual Jira projects via `UserProjectRegistration`. ADR-005 option C also adds a repo-mapping link table so metric queries can include Jira issues alongside a Git repository's GitHub issues (implemented in T4.2).

#### Entities

**`JiraProjectEntity`** — Table `jira_projects`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `data_source_id` | FK → data_source_configs CASCADE NOT NULL | |
| `project_key` | VARCHAR(32) NOT NULL | e.g. `PDA` |
| `project_name` | VARCHAR(255) | Human-readable label; resolved from Jira API on add |
| `base_url_normalized` | VARCHAR(255) NOT NULL | Lowercase, trailing-slash-stripped copy of `dataSource.baseUrl`; set by `@PrePersist`/`@PreUpdate` via `JiraUrl.normalize()` |
| `last_scan_at` | TIMESTAMPTZ | Updated after each successful collection |
| `created_at` / `updated_at` | TIMESTAMPTZ | `@PrePersist` / `@PreUpdate` |
| UNIQUE | `(base_url_normalized, project_key)` — `uq_jira_project_global` | One canonical row per upstream Jira project, mirrors `git_repositories.repo_full_name UNIQUE` (ADR-004 / ADR-002) |

**`UserProjectRegistration`** — Table `user_project_registrations`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK (GENERATED ALWAYS AS IDENTITY) | |
| `user_id` | FK → users CASCADE NOT NULL | |
| `project_id` | FK → jira_projects CASCADE NOT NULL | |
| UNIQUE | `(user_id, project_id)` | One registration per user per project |

**`JiraProjectRepoMapping`** — Table `jira_project_repo_mappings` (ADR-005 option C)

| Column | Type | Notes |
|---|---|---|
| `jira_project_id` | FK → jira_projects CASCADE NOT NULL | Composite PK |
| `repository_id` | FK → git_repositories CASCADE NOT NULL | Composite PK |

Metric queries in T4.2 join through this table to include Jira issues in repository-scoped aggregations.

#### Repositories

**`JiraProjectRepository`**
- `findAllByDataSource(DataSourceConfig)` → `List<JiraProjectEntity>`
- `findByDataSourceAndProjectKey(DataSourceConfig, String)` → `Optional`
- `findByBaseUrlNormalizedAndProjectKey(String, String)` → `Optional` — global canonical-row lookup used by `addProject` cross-DS detection (ADR-002)
- `findIdsByDataSourceIds(List<Long>)` → `List<Long>` — used by metric scoping

**`UserProjectRegistrationRepository`**
- `existsByUserAndProject(User, JiraProjectEntity)` → `boolean`
- `findByUserAndProject(User, JiraProjectEntity)` → `Optional`
- `findAllByUser(User)` → `List`
- `findDataSourceConfigsByUserId(Long)` → `List<DataSourceConfig>` — joins via `project.dataSource`
- `existsByUserIdAndDataSourceId(Long userId, Long dataSourceId)` → `boolean` — joins via `project.dataSource`

**`JiraProjectRepoMappingRepository`**
- `findAllByJiraProject(JiraProjectEntity)` → `List`
- `findAllByRepository(GitRepositoryEntity)` → `List`
- `existsByJiraProjectAndRepository(JiraProjectEntity, GitRepositoryEntity)` → `boolean`
- `findRepositoryIdsByJiraProjectIds(List<Long>)` → `List<Long>` — used by T4.2 metric queries

#### Services

**`JiraProjectService`** — Manages tracked Jira projects and subscriptions.
- `addProject(DataSourceConfig, projectKey, projectName)` — three-branch canonical logic: (1) global lookup by `(base_url_normalized, project_key)` finds same-DS row → idempotent return; (2) finds cross-DS row → returns canonical row, caller subscribes; (3) no row → insert. Uses `JiraUrl.normalize()` for both key derivation and the lookup.
- `listTrackedProjects(DataSourceConfig)` → `List<JiraProjectEntity>`
- `getProjectForUser(projectId, userId)` — checks DS ownership or active subscription; throws `ForbiddenException` otherwise.
- `deleteProject(projectId, userId)` — DS owner only.
- `subscribeUser(projectId, userId)` / `unsubscribeUser(projectId, userId)` — idempotent.
- `findByBaseUrlAndProjectKey(baseUrl, projectKey)` — looks up an existing `JiraProjectEntity` by Jira instance URL + key; used by `DataSourceService.create` to prevent duplicate datasources.
- `findSubscribedDataSourceConfigs(userId)` — datasources reachable via the user's Jira project subscriptions; used by `DataSourceService.listForUser`.
- `hasSubscriptionForDataSource(userId, dataSourceId)` — used by `DataSourceService.getForUser` to grant sync access.
- `listProjects(DataSourceConfig)` → `List<JiraProjectDto>` — calls Jira REST API `/rest/api/3/project/search` with pagination to list all available projects on the instance.

**Jira subscription model** mirrors the GitHub repo subscription model:
- User A creates a Jira DS with `projectKey=PDA` → `jira_projects` row created; user A is the DS owner.
- User B creates a Jira DS with the same `baseUrl` + `projectKey` → instead of a duplicate DS, `DataSourceService.create` finds the existing project via `findByBaseUrlAndProjectKey` and calls `subscribeUser` → `user_project_registrations` row for user B is created. The existing DS is returned.
- User B sees the DS in their list via `findSubscribedDataSourceConfigs`, can sync, but cannot delete.

**`JiraCollector`** — Collects issues for a single `JiraProjectEntity`.
- `collectIssues(JiraProjectEntity project)` — authenticates via Basic Auth (email:token), builds JQL scoped to the project key, paginates through results, upserts `IssueEntity` rows keyed by `(jira_project_id, external_id)`.

#### DTOs

| Record | Fields |
|---|---|
| `JiraProjectResponseDto` | `id`, `dataSourceId`, `dataSourceBaseUrl`, `projectKey`, `projectName`, `lastScanAt`, `subscribed` |
| `AttachProjectRequest` | `projectKey` (NotBlank), `projectName?` |
| `DiscoveredProjectDto` | `projectKey`, `projectName`, `alreadyAttached` |

#### Controllers

**`DataSourceJiraProjectController`** — `/api/datasources/{id}/projects`, `@PreAuthorize("isAuthenticated()")`

| Method | Path | Response | Description |
|---|---|---|---|
| GET | `` | `List<JiraProjectResponseDto>` | List projects tracked under datasource `{id}` (owner + subscribers) |
| POST | `` | 201 `JiraProjectResponseDto` | Attach a project to datasource `{id}` (owner only, idempotent; auto-subscribes caller) |
| DELETE | `/{projectId}` | 204 | Detach a project from datasource `{id}` (owner only; cascades issues via FK) |
| GET | `/discover-projects` | `200 List<DiscoveredProjectDto>` | List projects visible to stored token with `alreadyAttached` flag; cached 60 s (owner only) |

**`JiraProjectController`** (legacy) — `/api/jira-projects`, `@PreAuthorize("isAuthenticated()")` — Superseded by `DataSourceJiraProjectController`; kept for backward compatibility.

| Method | Path | Response | Description |
|---|---|---|---|
| GET | `` | `List<JiraProjectResponseDto>` | List tracked projects for a datasource (`?dataSourceId`) |
| GET | `/available` | `List<JiraProjectDto>` | Discover projects available from the Jira API |
| POST | `` | 201 `JiraProjectResponseDto` | Add a project to track (DS owner only) |
| DELETE | `/{id}` | 204 | Remove a tracked project (DS owner only) |
| POST | `/{id}/subscribe` | 200 | Subscribe current user to the project |
| DELETE | `/{id}/subscribe` | 204 | Unsubscribe current user |

#### Migration history

| Version | File | Summary |
|---|---|---|
| V32 | `V32__jira_projects.sql` | Create `jira_projects` + `user_project_registrations`; backfill from `data_source_configs.project_key`; add `jira_project_id` to `issues`; replace single unique constraint on issues with two partial indexes; create `jira_project_repo_mappings`; drop `project_key` from `data_source_configs` |
| V39 | `V39__jira_projects_canonical_row.sql` | Add `base_url_normalized` to `jira_projects`; backfill from datasource `base_url`; replace `uq_jira_project_ds_key (data_source_id, project_key)` with global `uq_jira_project_global (base_url_normalized, project_key)` (ADR-002 / ADR-004) |

---

### 3.8 Git Domain (Local Repositories)

#### Content-Addressed Access Model (ADR-004)

The platform stores each upstream GitHub repository as **one canonical row** in `git_repositories`,
identified by the globally-unique `repo_full_name` (`owner/repo`). This mirrors Git's own
content-addressed object model: identical content has one identity, one history.

```
User A (owner)  ─── DataSourceConfig ─── git_repositories (canonical) ─── git_commits
                                                │
User B (subscriber) ── UserRepoRegistration ───┘
```

When a second user wants to track a repository already registered by another user, the attach
endpoint (`POST /api/datasources/{id}/repos`) creates a `UserRepoRegistration` row — never a
second `git_repositories` row. `git_commits.hash UNIQUE` enforces the same guarantee at the
storage layer.

**Guarantees:**
- One commit history per upstream repository; no duplicate ingestion.
- Metric queries over `repoIds` attribute by `author_email` / `githubLogin`; shared repos do
  not pollute individual metrics.
- Collection is driven by the canonical datasource's token and sync schedule.

**Known trade-offs (intentional ownership semantics, not bugs):**
- A subscriber inherits the canonical datasource's sync schedule. They cannot trigger
  independent collection.
- If the canonical datasource is deleted, all `UserRepoRegistration` rows for that repo are
  removed via cascade — subscribers lose access.
- Detaching the canonical row is blocked (409) while other subscriptions exist; subscribers
  must unsubscribe first.

These trade-offs are discussed academically in thesis §3 (System Design) and §8.7 (Threats to
Validity, under "data-model invariants as validity preconditions").

See `docs/adr/ADR-004-cross-ds-repo-sharing.md` for the full decision record and rejected
alternatives (independent rows per DS, explicit attachment join table).

#### Entities

**`GitRepositoryEntity`** — Table `git_repositories`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `data_source_id` | FK → data_source_configs CASCADE | Canonical owner datasource |
| `repo_type` | VARCHAR(16) NOT NULL | `LOCAL` or `GITHUB`; discriminator column (ADR-003); enforced by CHECK constraints `chk_repo_local_path` and `chk_repo_github_fullname` (V36) |
| `name` | VARCHAR(255) | |
| `local_path` | VARCHAR(1024) NULLABLE | Null for GitHub repos |
| `repo_full_name` | VARCHAR(255) UNIQUE | `owner/repo`; globally unique (see ADR-004); null for local repos |
| `last_fetched_commit_hash` | VARCHAR(64) | Watermark for incremental sync |
| `last_scan_at` | TIMESTAMPTZ | |
| `collect_issues` | BOOLEAN DEFAULT FALSE | When true, GITHUB-type sync also collects issues |

**`GitCommitEntity`** — Table `git_commits` (sequence allocationSize=500)

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | Sequence increment 500 |
| `repository_id` | FK → git_repositories CASCADE NOT NULL | |
| `hash` | VARCHAR(64) UNIQUE NOT NULL | SHA-1 |
| `author_name` / `author_email` | VARCHAR(255) NOT NULL | |
| `author_date` | TIMESTAMPTZ | |
| `message` | TEXT | |
| `additions` / `deletions` / `files_changed` | INT | Populated by enrichment |
| `parent_hash` | VARCHAR(64) | Null for root commits |
| `stats_status` | VARCHAR(20) DEFAULT 'COMPLETE' | PENDING / COMPLETE / FAILED / SKIPPED |
| `stats_fetched_at` | TIMESTAMPTZ | When enrichment ran |
| `stats_attempts` | INT DEFAULT 0 | Retry counter |

**`StatsStatus`** (Enum) — `PENDING`, `COMPLETE`, `FAILED`, `SKIPPED`.

**`UserRepoRegistration`** — Table `user_repo_registrations`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK (GENERATED ALWAYS AS IDENTITY) | |
| `user_id` | FK → users CASCADE | |
| `repo_id` | FK → git_repositories CASCADE | |
| UNIQUE | `(user_id, repo_id)` | One registration per user per repo |

The datasource a subscription belongs to is derived via `repo_id → git_repositories.data_source_id` (dropped V34: `data_source_id` denormalised column).

#### Repositories

**`GitRepositoryEntityRepository`**
- `findAllByDataSourceConfig(DataSourceConfig)` → `List`
- `findByRepoFullName(String)` → `Optional`
- `@Query` `findIdsByTeamIds(List<Long> teamIds)` → `List<Long>` (repos under team data sources)
- `findAllByIdWithDataSourceConfig(Collection<Long> ids)` — eager-loads `dataSourceConfig`

**`GitCommitEntityRepository`**
- `findByHash(String)` → `Optional`
- `countByRepositoryId(Long)` → `long`
- `findHashesByRepositoryId(Long)` → `List<String>`
- `findByRepositoryIdOrderByAuthorDateDesc(Long, Pageable)` → `Page`
- Aggregation queries (all return `List<Object[]>`):
  - `aggregateChurnDailyByRepoIds(repoIds, from, to)` — `(day, repoId, additions, deletions)`
  - `aggregateCommitsDailyByRepoIds(repoIds, from, to)` — `(day, repoId, count, avgSize)`
  - `aggregateCommitsDailyByRepoIdsAndAuthorEmail(repoIds, email, from, to)` — same, filtered by author
  - `aggregateChurnDailyByRepoIdsAndAuthorEmail(repoIds, email, from, to)`
  - `findCommitDetailsByRepoIdsAndAuthorEmail(repoIds, email, from, to)` — `(authorDate, additions, deletions)` for after-hours + refactor
  - `countTotalCommitsByRepoIds(repoIds, from, to)` — `(repoId, count)` denominator for knowledge silo
  - `countCommitsByRepoIdsAndAuthorEmail(repoIds, email, from, to)` — `(repoId, count)` numerator
  - `findCommitsForPr(repo, prNumber)` — commits mentioning `#prNumber` in message
  - `findCommitsForPrByRepoIds(repoIds, prNumber)` — same across multiple repos
- Enrichment queries:
  - `findPendingCommitsForEnrichment(StatsStatus, Pageable)` — newest-first across all repos
  - `findRepositoryIdsWithStatsStatus(StatsStatus)` → `List<Long>`
  - `findPendingCommitsForRepository(StatsStatus, repositoryId, Pageable)` — per-repo batch

**`UserRepoRegistrationRepository`**
- `existsByUserIdAndRepositoryId(Long, Long)` → `boolean`
- `existsByUserIdAndDataSourceConfig_Id(Long userId, Long dsId)` → `boolean` — joins through `repository.dataSourceConfig`
- `findByUserIdAndRepositoryId(Long, Long)` → `Optional`
- `findRepoIdsByUserId(Long)` → `List<Long>`
- `findRepoIdsByUserIdAndDataSourceId(Long userId, Long dataSourceId)` → `List<Long>` — joins through `repository.dataSourceConfig`
- `findDataSourceConfigsByUserId(Long userId)` → `List<DataSourceConfig>` — joins through `repository.dataSourceConfig`

#### Services

**`GitLocalCollector`** — Collects commits from a local `.git` directory using JGit.
- `collectForRepository(Long repoId, JobState)` → `int`
  - Opens repo via `Git.open(new File(localPath))`.
  - **Phase 1** (single-threaded): walks `git.log().call()`, extracts hash/author/date/message, stops at `lastFetchedCommitHash`. Filters already-known hashes.
  - **Phase 2** (parallel, up to 4 threads): computes diff stats (additions/deletions/filesChanged) via `DiffFormatter`.
  - Batch-saves every 500 commits; updates `lastFetchedCommitHash` and `lastScanAt`.
  - Constants: `BATCH_SIZE=500`, `DIFF_THREADS=min(CPU cores, 4)`.

**`GitRepositoryService`** — `registerLocalRepo(Long userId, RegisterLocalRepoRequest)`, `listReposForUser(Long userId)`, `getRepoForUser(Long userId, Long repoId)` (ownership check), `listCommitsForRepo(Long userId, Long repoId, Pageable)`.

**`RepoService`** — Unified subscription layer.
- `getById(Long repoId)` → `GitRepositoryEntity`.
- `listAccessible(Long dataSourceId?)` → `List<RepoDto>` — merges repos from user's own data sources + team data sources + subscriptions; deduplicates; sets `subscribed` flag; generates `repoUrl` from API base URL.
- `subscribe(Long repoId)` / `unsubscribe(Long repoId)` — creates/removes `UserRepoRegistration`.

#### Controllers

**`GitLocalController`** — `/api/git/local`, `@PreAuthorize("isAuthenticated()")`

| Method | Path | Description |
|---|---|---|
| POST | `/repos` | Register local repo |
| GET | `/repos` | List user's local repos |
| GET | `/repos/{repoId}` | Get repo |
| GET | `/repos/{repoId}/commits?page&size` | Paginated commits |
| POST | `/repos/{repoId}/collect` | Collect commits synchronously |

**`RepoController`** — `/api/repos`, `@PreAuthorize("isAuthenticated()")`

| Method | Path | Description |
|---|---|---|
| GET | `/?dataSourceId?` | List accessible repos |
| POST | `/{repoId}/subscribe` | Subscribe |
| DELETE | `/{repoId}/subscribe` | Unsubscribe |

#### DTOs (Records)

`GitRepositoryDto` (id, dataSourceId, name, localPath, lastFetchedCommitHash, lastScanAt), `GitCommitDto` (id, hash, authorName, authorEmail, authorDate, message, additions, deletions, filesChanged, parentHash), `RegisterLocalRepoRequest`, `RepoDto` (id, name, repoFullName, localPath, dataSourceId, subscribed, repoUrl).

---

### 3.9 GitHub Domain

#### Entities

**`GitHubPullRequestEntity`** — Table `github_pull_requests`, UNIQUE `(repository_id, number)`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `repository_id` | FK → git_repositories CASCADE | |
| `number` | INT | GitHub PR number |
| `title` | TEXT | |
| `author_login` | VARCHAR(255) | GitHub username |
| `state` | VARCHAR(16) | `open` / `closed` |
| `merged` | BOOLEAN | |
| `lead_time_hours` | BIGINT | `mergedAt − createdAt` in hours |
| `created_at` / `updated_at` / `closed_at` / `merged_at` | TIMESTAMPTZ | |
| `additions` / `deletions` / `changed_files` | INT | Enrichment phase |
| `comments_count` / `review_comments_count` / `commits_count` | INT | Enrichment phase |
| `stats_status` | VARCHAR(20) DEFAULT 'COMPLETE' | PENDING / COMPLETE / FAILED / SKIPPED |
| `stats_fetched_at` | TIMESTAMPTZ | |
| `stats_attempts` | INT DEFAULT 0 | |

**`GitHubPrReviewEntity`** — Table `github_pr_reviews`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK (GENERATED ALWAYS AS IDENTITY) | |
| `pr_id` | FK → github_pull_requests CASCADE | |
| `reviewer_login` | VARCHAR(255) | |
| `state` | VARCHAR(32) | APPROVED / CHANGES_REQUESTED / COMMENTED |
| `submitted_at` | TIMESTAMPTZ | |

#### Repositories

**`GitHubPullRequestRepository`**
- `findByRepositoryAndNumber(repo, number)` → `Optional`
- `findByRepositoryOrderByCreatedAtDesc(repo, Pageable)` → `Page`
- Aggregation (per-login filtered):
  - `aggregatePrCreatedDailyByRepoIdsAndAuthorLogin(repoIds, login, from, to)` — `(day, repoId, count)`
  - `aggregatePrMergedDailyByRepoIdsAndAuthorLogin(repoIds, login, from, to)`
  - `findMergedLeadTimesByRepoIdsAndAuthorLogin(repoIds, login, from, to)` — `(repoId, createdAt, mergedAt)`
  - `findMergedPrsByRepoIdsAndAuthorLogin(repoIds, login, from, to)` → `List<GitHubPullRequestEntity>`
- Enrichment:
  - `findPendingPrsForRepository(StatsStatus, repoId, Pageable)` — newest-first
  - `findRepositoryIdsWithStatsStatus(StatsStatus)` → `List<Long>`

**`GitHubPrReviewRepository`**
- `deleteAllByPullRequest(pr)`, `deleteAllByPullRequestIn(List<pr>)`
- `@Query("SELECT r.pullRequest.id, MIN(r.submittedAt) FROM ... WHERE r.pullRequest.id IN :prIds GROUP BY r.pullRequest.id")` `findFirstReviewTimestampsByPrIds(List<Long>)` — returns `List<Object[]>` with `(prId, firstReviewInstant)`

#### Services

**`GitHubClientFactory`** — `createClient(DataSourceConfig)` → Kohsuke `GitHub` instance authenticated with the decrypted token. Resolves blank/null/`github.com` URLs → `api.github.com`.

**`GitHubRepositoryService`** — `registerGitHubRepo(Long userId, Long dataSourceId, String fullName)`:
- Fast path: repo already exists → just create `UserRepoRegistration`.
- Slow path: create `GitRepositoryEntity` + `UserRepoRegistration`.

**`GitHubCommitIngestService`** — Phase A of commit collection.
- Constants: `PAGE_SIZE=100`, `SAVE_BATCH_SIZE=500`, `PAGE_PAUSE_MS=200`.
- `ingestForRepository(Long gitRepoId, JobState)` → `IngestResult(savedEntities, apiBase, token)`:
  - Pages `GET /repos/{owner}/{repo}/commits?per_page=100` until `lastFetchedCommitHash` hit.
  - Skips known hashes. Saves with `statsStatus=PENDING`.
  - Handles 429/403 with `Retry-After`. Updates `lastFetchedCommitHash` + `lastScanAt`.

**`GitHubCommitStatsEnrichmentService`** — Phase B+C of commit enrichment.
- Constants: `IMMEDIATE_ENRICH_LIMIT=150`, `BATCH_SIZE=50`, `MAX_ATTEMPTS=3`, `MIN_INTERVAL_NS=700_000_000` (~1.4 req/s), `INITIAL_BACKOFF_MS=5000`, `MAX_BACKOFF_MS=120_000`.
- `enrichImmediate(List<GitCommitEntity>, apiBase, token, repoFullName)` — 2-worker thread pool, enriches top `IMMEDIATE_ENRICH_LIMIT` commits (sorted newest-first).
- `processPendingBatchForRepo(apiBase, token, repoFullName, repoId)` → `int` — background batch, up to `BATCH_SIZE` per call.
- `enrichSingle(entity, ...)` — `GET /repos/{owner}/{repo}/commits/{sha}`. On 200: applies `additions`/`deletions`/`filesChanged`, sets `COMPLETE`. On 403 diff-too-large: sets `SKIPPED`. On 404/422: sets `FAILED`. On 5xx: increments attempts, backs off.
- Rate limiting: synchronized token bucket on `MIN_INTERVAL_NS`; 100–300 ms jitter; proactive throttle when `X-RateLimit-Remaining < 100`; exponential backoff on error.

**`GitHubCollector`** — Orchestrates commit collection:
1. `GitHubCommitIngestService.ingestForRepository()` (Phase A).
2. Sort saved by `authorDate` desc.
3. `GitHubCommitStatsEnrichmentService.enrichImmediate()` (Phase B — top 150).
4. Log remaining PENDING count (Phase C handled by scheduler).

**`GitHubPrCollector`** — Orchestrates PR collection:
1. `GitHubPullRequestCollector` (ingest): pages all PRs, upserts by `(repoId, number)`, saves with `statsStatus=PENDING`.
2. Sort saved by `createdAt` desc.
3. `GitHubPrStatsEnrichmentService.enrichImmediate()` (Phase B — top 150): fetches reviews (delete+save in `github_pr_reviews`) and detail stats per PR.
4. Phase C handled by scheduler.

**`GitHubPullRequestCollector`** — Ingest-only service. Pages PRs from GitHub API, upserts into `github_pull_requests`. Also used by `GitHubPullRequestController` for paginated listing. Returns `IngestResult(savedEntities, apiBase, token)`.

**`GitHubPrStatsEnrichmentService`** — Enriches PR stats. Same rate-limit pattern as commit enrichment. `enrichSingle(pr, ...)` — `GET /repos/{owner}/{repo}/pulls/{number}` for additions/deletions/changedFiles/commitsCount; `GET .../reviews` for review list (delete+save all reviews then mark PR `COMPLETE`).

**`GitHubIssuesCollector`** — Fetches all issues (open + closed) via Kohsuke API, maps to `IssueEntity` with `externalId = owner/repo#{number}`. Upserts by `(dataSourceId, externalId)`. Accepts either a `GitRepositoryEntity` or a bare `repoFullName` string.

#### Helpers

**`ParsingHelper`** — `resolveApiBase(String configuredBaseUrl)` — null/blank/`github.com` → `https://api.github.com`; strips `/api/v3` suffix for GitHub Enterprise.

#### Controllers

**`GitHubController`** — `/api/github`

| Method | Path | Description |
|---|---|---|
| POST | `/repos` | Register GitHub repo by `owner/repo` |
| POST | `/repos/{repoId}/collect` | Phase A+B commit collection |

**`GitHubPullRequestController`** — `/api/github/repos/{repoId}/pull-requests`

| Method | Path | Description |
|---|---|---|
| POST | `/collect` | Phase A+B PR collection |
| GET | `/?page&size` | Paginated PR listing |

#### DTOs (Records)

`GitHubPullRequestDto` (id, number, title, authorLogin, state, merged, createdAt, closedAt, mergedAt, additions, deletions, changedFiles, commentsCount, reviewCommentsCount, commitsCount), `RegisterGitHubRepoRequest` (dataSourceId, fullName).

---

### 3.10 Issues Domain

#### Entity

**`IssueEntity`** — Table `issues`, UNIQUE `(data_source_id, external_id)`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `data_source_id` | FK → data_source_configs CASCADE | |
| `external_id` | VARCHAR(255) | `PROJ-42` (Jira) or `owner/repo#17` (GitHub) |
| `repository_id` | FK → git_repositories | Nullable; GitHub issues only |
| `repo_name` | TEXT | Denormalized |
| `title` / `description` | TEXT | |
| `state` | VARCHAR(32) | `open` / `closed` / `done` |
| `assignee` / `creator` | VARCHAR(255) | |
| `created_at` / `updated_at` / `closed_at` | TIMESTAMPTZ | |
| `labels` | TEXT | Comma-separated |

#### Repository

**`IssueRepository`**
- `aggregateIssuesCreatedDailyByRepoIds(repoIds, from, to)` → `List<Object[]>` — `(day, repoId, count)`
- `aggregateIssuesClosedDailyByRepoIds(repoIds, from, to)` → `List<Object[]>`
- `findIssueLeadTimesByRepoIds(repoIds, from, to)` → `List<Object[]>` — `(repoId, createdAt, closedAt)`

#### Services

**`IssueService`** — Provides paginated listing of issues by datasource.

**`JiraCollector`** — Calls Jira Cloud `/rest/api/3/search/jql` with Basic Auth. JQL uses the datasource's `projectKey` when set (`project = "KEY" ORDER BY created DESC`), otherwise falls back to `assignee = currentUser()`. Paginates with `startAt`/`maxResults`. Maps to `IssueEntity` (`externalId` = issue key, `closedAt` from `resolutiondate`). Upserts by `(dataSourceId, externalId)`.

#### Controller

**`IssuesController`** — `/api/issues`

| Method | Path | Description |
|---|---|---|
| POST | `/jira/{dataSourceId}/collect` | Collect from Jira |
| POST | `/github/{dataSourceId}/repos/{owner}/{repo}/collect` | Collect GitHub Issues |
| GET | `/{dataSourceId}?page&size` | List issues (paginated) |

---

### 3.11 Metrics Domain

#### Entity

**`MetricSnapshot`** — Table `metric_snapshots`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `user_id` | FK → users NOT NULL | |
| `team_id` | FK → teams | NULL = personal; NOT NULL = team scope |
| `repository_id` | FK → git_repositories | NULL = all-repo aggregate |
| `date` | DATE NOT NULL | Calendar day |
| `metric_type` | VARCHAR(64) NOT NULL | One of 19 MetricType values |
| `value` | DOUBLE PRECISION | |
| `period_from` / `period_to` | DATE | Window boundaries for aggregate metrics |

Indexes: `(user_id, repository_id, date, metric_type)`, `(user_id, team_id, date, metric_type)`.

#### MetricType (Enum) — 19 values

| Value | Description |
|---|---|
| `DAILY_COMMITS_COUNT` | Commits authored on a given day |
| `DAILY_COMMITS_AVG_SIZE` | Avg (additions + deletions) per commit |
| `DAILY_PR_CREATED` | PRs opened on a given day |
| `DAILY_PR_MERGED` | PRs merged on a given day |
| `DAILY_ISSUES_CREATED` | Issues created on a given day |
| `DAILY_ISSUES_CLOSED` | Issues closed on a given day |
| `DAILY_CHURN_RATIO` | `deletions / (additions + deletions)` |
| `PR_LEAD_TIME_HOURS_MEDIAN` | Median hours from PR open to merge |
| `PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN` | Median hours from first commit to PR merge |
| `REVIEW_RESPONSE_TIME_HOURS_MEDIAN` | Median hours from PR open to first review |
| `ISSUE_LEAD_TIME_HOURS_MEDIAN` | Median hours from issue create to close |
| `FOCUS_RATIO_DAYS_TASKS` | Presence indicator (1.0) for weekdays with commits; ratio computed on read |
| `AFTER_HOURS_COMMIT_RATIO` | Share of commits outside 09:00–18:00 Mon–Fri in user's timezone |
| `REFACTOR_RATIO` | Share of commits where deletions > additions |
| `DEEP_WORK_STREAK_DAYS` | Longest consecutive run of commit days |
| `MERGE_TO_MAIN_FREQUENCY_PER_WEEK` | Average merges to main per ISO week (DORA proxy) |
| `KNOWLEDGE_SILO_SCORE` | Max commit share across repos — bus-factor risk indicator |
| `PR_SIZE_COMPLEXITY_SCORE` | Median `(additions + deletions) / max(commitsCount, 1)` per PR |
| `MERGE_WITHOUT_REVIEW_RATIO` | Share of merged PRs with zero reviews |

#### Repository

**`MetricSnapshotRepository`**
- Standard JPQL finders by user/team/metricType/date/repo.
- `findExisting(userId, teamId, repoId, date, metricType, periodFrom, periodTo)` — native SQL upsert guard using `IS NOT DISTINCT FROM` for nullable dimensions.
- `findByUserIdsAndTeamIdAndMetricTypeAndDateBetween(userIds, teamId, type, from, to)` — team summary query.
- `getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo` / `...AndRepositoryAndDateFromAndTo` — exact-period queries for aggregate metrics (lead times stored with periodFrom/periodTo).

#### Services

**`MetricsService`** — Core calculation engine.
- `calculateDailyMetrics(Long userId, LocalDate from, LocalDate to)` — personal scope (team=null).
- `calculateForTeam(Long teamId, Long requestingUserId, LocalDate from, LocalDate to)` — team scope; verifies requester is manager or ADMIN.
- **Repo resolution**: personal → `UserRepoRegistration`; fallback to team membership repos. Team → `DataSourceConfig.team`.
- **Attribution**: always filtered by `user.email` (commits) or `user.githubLogin` (PRs) — shared repos never pollute individual metrics.
- **Private calc methods** (one per metric group):
  - `calcDailyCommits` → `DAILY_COMMITS_COUNT`, `DAILY_COMMITS_AVG_SIZE`
  - `calcDailyPrs` → `DAILY_PR_CREATED`, `DAILY_PR_MERGED`
  - `calcDailyIssues` → `DAILY_ISSUES_CREATED`, `DAILY_ISSUES_CLOSED`
  - `calcDailyChurn` → `DAILY_CHURN_RATIO`
  - `calcLeadTimePrs` → `PR_LEAD_TIME_HOURS_MEDIAN`
  - `calcLeadTimeIssues` → `ISSUE_LEAD_TIME_HOURS_MEDIAN`
  - `calcLeadTimeFirstCommitToMerge` → `PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN`
  - `calcReviewResponseTime` → `REVIEW_RESPONSE_TIME_HOURS_MEDIAN`
  - `calcFocusRatio` → `FOCUS_RATIO_DAYS_TASKS` (saves 1.0 only for active weekdays)
  - `calcAfterHoursRatioAndRefactorRatio` → `AFTER_HOURS_COMMIT_RATIO`, `REFACTOR_RATIO` (one DB call)
  - `calcDeepWorkStreak` → `DEEP_WORK_STREAK_DAYS` (TreeSet → longest consecutive run)
  - `calcMergeToMainFrequency` → `MERGE_TO_MAIN_FREQUENCY_PER_WEEK` (ISO week grouping)
  - `calcKnowledgeSilo` → `KNOWLEDGE_SILO_SCORE` (max user-share across repos)
  - `calcPrSizeComplexity` → `PR_SIZE_COMPLEXITY_SCORE` (sorted median)
  - `calcMergeWithoutReview` → `MERGE_WITHOUT_REVIEW_RATIO` (PRs with no review events)
- **`saveMetric(user, team, date, type, value, repo, periodFrom, periodTo)`** — native SQL upsert guard; updates if exists, inserts if not.

**`MetricSnapshotService`** — Query facade over `MetricSnapshotRepository`.
- `getMetricSnapshotsByUserAndMetricTypeAndDateBetween(user, type, from, to)`
- `getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween(user, type, repo, from, to)`
- `getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(user, type, from, to)` — exact-period query for aggregate metrics
- `getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateFromAndTo(user, type, repo, from, to)`
- `getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(user, team, type, from, to)`
- `getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndDateBetween(userIds, teamId, type, from, to)`

**`MetricsScheduler`** — `@Scheduled(cron = "0 0 1 * * ?")` (daily 01:00 UTC). Iterates all users, calls `calculateDailyMetrics(userId, yesterday, yesterday)`. Per-user exceptions caught and logged as warnings.

#### DTOs

| Class | Fields |
|---|---|
| `MetricPointDto` | `date`, `value`, `metricType`, `repositoryId?`, `repositoryName?` |
| `MetricAggregateDto` | `metricType`, `value`, `periodFrom?`, `periodTo?` |
| `TeamMetricPointDto` | `date`, `value`, `metricType`, `userId?`, `username` |
| `MemberSummaryDto` | `userId`, `username`, `metrics: Map<MetricType, Double>`, `hasCustomAvatar`, `avatarPreset`, `lastActiveAt?`, `email?` |

#### Controller

**`MetricsController`** — `/api/metrics`, `@PreAuthorize("isAuthenticated()")`, injects: `MetricSnapshotService`, `MetricsService`, `RepoService`, `TeamService`, `UserService`, `CheckHelper`.

**Personal endpoints:**

| Method | Path | Query | Response |
|---|---|---|---|
| POST | `/calculate` | `from`, `to` | void |
| GET | `/daily-commits` | `from`, `to`, `repoId?` | `List<MetricPointDto>` |
| GET | `/daily-pr-created` | `from`, `to`, `repoId?` | `List<MetricPointDto>` |
| GET | `/daily-pr-merged` | `from`, `to`, `repoId?` | `List<MetricPointDto>` |
| GET | `/daily-issues-created` | `from`, `to`, `repoId?` | `List<MetricPointDto>` |
| GET | `/daily-issues-closed` | `from`, `to`, `repoId?` | `List<MetricPointDto>` |
| GET | `/daily-churn` | `from`, `to`, `repoId?` | `List<MetricPointDto>` |
| GET | `/pr-lead-time` | `from`, `to`, `repoId?` | `MetricAggregateDto` |
| GET | `/pr-first-commit-lead-time` | `from`, `to`, `repoId?` | `MetricAggregateDto` |
| GET | `/review-response-time` | `from`, `to`, `repoId?` | `MetricAggregateDto` |
| GET | `/issue-lead-time` | `from`, `to`, `repoId?` | `MetricAggregateDto` |
| GET | `/focus-ratio/series` | `from`, `to` | `List<MetricPointDto>` (active days only, value=1.0) |
| GET | `/focus-ratio` | `from`, `to` | `MetricAggregateDto` (activeDays / totalWeekdays) |
| GET | `/after-hours-ratio` | `from`, `to` | `MetricAggregateDto` |
| GET | `/refactor-ratio` | `from`, `to` | `MetricAggregateDto` |
| GET | `/deep-work-streak` | `from`, `to` | `MetricAggregateDto` |
| GET | `/merge-frequency` | `from`, `to` | `MetricAggregateDto` |
| GET | `/knowledge-silo` | `from`, `to`, `repoId?` | `MetricAggregateDto` |
| GET | `/pr-size-complexity` | `from`, `to`, `repoId?` | `MetricAggregateDto` |
| GET | `/merge-without-review` | `from`, `to`, `repoId?` | `MetricAggregateDto` |

**Team endpoints** (MANAGER / ADMIN):

| Method | Path | Response |
|---|---|---|
| POST | `/teams/{teamId}/calculate?from&to` | void |
| GET | `/teams/{teamId}/daily-commits?from&to` | `List<TeamMetricPointDto>` |
| GET | `/teams/{teamId}/summary?from&to` | `List<MemberSummaryDto>` |
| GET | `/teams/{teamId}/members/{memberId}/summary?from&to` | `MemberSummaryDto` |
| GET | `/teams/{teamId}/members/{memberId}/daily-commits?from&to` | `List<MetricPointDto>` |
| GET | `/teams/{teamId}/members/{memberId}/daily-pr-created?from&to` | `List<MetricPointDto>` |
| GET | `/teams/{teamId}/members/{memberId}/daily-churn?from&to` | `List<MetricPointDto>` |

---

### 3.12 AI Domain

The AI layer generates natural-language summaries and metric explanations from pre-aggregated metric snapshots using a locally running Ollama LLM. All inference is fully offline — no data leaves the machine.

#### Entities

**`MetricSummaryEntity`** — Table `metric_summaries`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `user_id` | FK → users | |
| `period_from` / `period_to` | DATE | Summary period |
| `scope` | VARCHAR(32) | `PERSONAL` / `REPOSITORY` / `TEAM` |
| `repo_name` | VARCHAR(255) | Null unless scope = REPOSITORY |
| `overview` | TEXT | 1–2 sentence narrative |
| `insights` | TEXT | JSON array of strings |
| `recommendations` | TEXT | JSON array of strings |
| `model_name` | VARCHAR(255) | e.g. `llama3.2` |
| `raw_model_output` | TEXT | Verbatim model response |
| `created_at` | TIMESTAMPTZ DEFAULT now() | |

#### Repository

**`MetricSummaryRepository`** — `findTopByUserOrderByCreatedAtDesc(User)`, `findByUserAndPeriodFromAndPeriodTo(User, from, to)`.

#### Services

**`MetricsAiService`** — Core AI service. `@Cacheable(value = "ai_summaries", key = "{#user.id, #from, #to, #repoId}")`.

- **Context metric types used**: `DAILY_COMMITS_COUNT`, `DAILY_PR_CREATED`, `DAILY_PR_MERGED`, `DAILY_ISSUES_CREATED`, `DAILY_ISSUES_CLOSED`, `DAILY_CHURN_RATIO`, `PR_LEAD_TIME_HOURS_MEDIAN`, `PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN`, `ISSUE_LEAD_TIME_HOURS_MEDIAN`, `REVIEW_RESPONSE_TIME_HOURS_MEDIAN`, `FOCUS_RATIO_DAYS_TASKS`.

- **Routing**: daily-sum metrics (`DAILY_*`) → `dateBetween` query; aggregate metrics (lead times, review time) → exact `periodFrom/periodTo` query.

- **`generateSummary(User, from, to, repoId)`** — personal/repo scope:
  1. Fetches snapshots for all context metric types.
  2. Builds `AggregatedMetricsContext` — per metric: `{min, max, median, total, trendPct, anomaly}`.
  3. `trendPct` — compares avg of first half vs. second half of the time series.
  4. `anomaly` — true if any value is more than 2 standard deviations from the mean.
  5. Serialises context to JSON, builds structured system + user prompt.
  6. Calls `LlmClient.complete(model, systemPrompt, userPrompt)`.
  7. Parses JSON response into `MetricsSummaryDto`; strips markdown code fences if present.
  8. Result cached in `ai_summaries` by `(userId, from, to, repoId)`.

- **`generateTeamSummary(User requestingUser, Long teamId, from, to)`** — team scope (MANAGER or ADMIN only):
  1. Builds `TeamMetricsContext` — per member: `{username, metrics: Map<MetricType, aggregatedValue>}`.
  2. Same prompt/parse flow as personal summary.
  3. Cached by `("team", teamId, from, to)`.

- **System prompt design**: instructs the model to return only a JSON object with fields `overview`, `insights` (5–8 items), `recommendations` (3–5 items). Priority order for insights: Churn Ratio → Focus Ratio → anomalies → remaining metrics. No markdown, no extra text.

**`OllamaLlmClient`** (`LlmClient` impl) — Posts to `{ollamaBaseUrl}/api/chat` with model, messages, stream=false, seed, num_predict. Extracts `message.content` from response.

#### Scheduler

**`MetricsSummaryScheduler`** — `@Scheduled(cron = "0 0 8 * * MON", zone = "UTC")`.
- Runs every Monday at 08:00 UTC.
- Iterates all users, calls `metricsAiService.generateSummary(user, lastMonday, lastSunday, null)`.
- Saves result as `MetricSummaryEntity` via `MetricSummaryRepository`.
- Per-user failures are logged but do not abort the run.

#### DTOs

| Class | Fields |
|---|---|
| `MetricsSummaryDto` | `from`, `to`, `scope`, `contextRepoName?`, `headline`, `overview`, `insights: List<InsightDto{kind,text,metric}>`, `recommendations: List<String>`, `rawModelOutput`, `modelName` |
| `AiResponseDto` (record) | `headline`, `overview`, `insights: List<InsightDto>`, `recommendations: List<String>` — inner record `InsightDto(kind, text, metric)` |
| `AggregatedMetricsContext` | `from`, `to`, `repoName?`, `metrics: Map<String, MetricAggregate{min,max,median,total,trendPct,anomaly}>` |
| `TeamMetricsContext` | `from`, `to`, `teamName`, `memberCount`, `members: List<MemberMetrics{username, metrics: Map<String,Double>}>` |
| `MetricsContext` | Per-day snapshot list (used internally) |

#### Controller

**`AiSummaryController`** — `/api/ai`, `@PreAuthorize("isAuthenticated()")`

| Method | Path | Query | Description |
|---|---|---|---|
| GET | `/summary` | `from`, `to`, `repoId?` | Personal/repo AI summary (cached) |
| GET | `/team/{teamId}/summary` | `from`, `to` | Team AI summary (MANAGER/ADMIN) |
| GET | `/explain` | `metricLabel`, `metricDescription`, `metricValue`, `from`, `to` | Explain a single metric in context |
| GET | `/summaries/latest` | — | Retrieve the user's most recent stored summary |

---

### 3.13 Email Service

**`EmailService`** — Dependencies: `JavaMailSender`; config: `fromEmail` (from `spring.mail.username`).
- `sendPasswordResetEmail(String toEmail, String resetLink)` — Sends via `SimpleMailMessage` over configured SMTP/STARTTLS.

---

## 4. Database Design

### 4.1 Schema Evolution — 43 Flyway Migrations

| Version | File | What it does |
|---|---|---|
| V1 | `V1__initial.sql` | Creates `users` (id, username, email, password_hash, timezone) and `data_source_configs` (id, user_id FK, type, name, base_url, path, api_token_encrypted, enabled, last_success_sync) |
| V2 | `V2__commits_repo.sql` | Creates `git_repositories` (id, data_source_id FK, name, local_path NOT NULL, last_fetched_commit_hash, last_scan_at) and `git_commits` (id, repository_id FK, hash UNIQUE, author_name, author_email, author_date, message, additions, deletions, files_changed, parent_hash) |
| V3 | `V3__edit_repos.sql` | ALTER `git_repositories` DROP NOT NULL on `local_path` (GitHub repos have no path) |
| V4 | `V4__github_pull_requests.sql` | Creates `github_pull_requests` (id, repository_id FK, number, title, author_login, state, merged, created/updated/closed/merged_at, additions, deletions, changed_files, counts), UNIQUE(repository_id, number) |
| V5 | `V5__issues.sql` | Creates `issues` (id, data_source_id FK, external_id, title, description, state, assignee, creator, created/updated/closed_at, labels), UNIQUE(data_source_id, external_id) |
| V6 | `V6__metrics_snapshot.sql` | Creates `metric_snapshots` (id, user_id FK, repository_id FK nullable, date, metric_type, value, dimensions_json), index `ix_metric_user_repo_date_type`; ALTER `github_pull_requests` ADD `lead_time_hours` BIGINT |
| V7 | `V7__add_reponame.sql` | ALTER `issues` ADD `repo_name` TEXT |
| V8 | `V8__edit_github_constraints.sql` | ALTER `git_commits.message` and `github_pull_requests.title` VARCHAR → TEXT |
| V9 | `V9__edit_issues.sql` | ALTER `issues` ADD `repository_id` BIGINT FK → `git_repositories` |
| V10 | `V10__add_roles.sql` | ALTER `users` ADD `role` VARCHAR(32) DEFAULT 'DEVELOPER'; index on `(role)` |
| V11 | `V11__refresh_tokens.sql` | Creates `refresh_tokens` (id, user_id FK CASCADE, token UNIQUE, expires_at, revoked DEFAULT FALSE, replaced_by, created_at); indexes on token and user_id |
| V12 | `V12__password_reset_tokens.sql` | Creates `password_reset_tokens` (id, user_id FK CASCADE, token UNIQUE, expires_at, used DEFAULT FALSE, created_at); indexes on token and user_id |
| V13 | `V13__teams.sql` | Creates `teams` (id, name, manager_id FK CASCADE, created_at) and `team_members` (team_id FK CASCADE, user_id FK CASCADE, PK); indexes on manager_id and user_id |
| V14 | `V14__user_token_version.sql` | ALTER `users` ADD `token_version` INT DEFAULT 0 |
| V15 | `V15__metric_snapshot_period_columns.sql` | ALTER `metric_snapshots` ADD `period_from` DATE, ADD `period_to` DATE, DROP `dimensions_json` |
| V16 | `V16__datasource_team_scope.sql` | ALTER `data_source_configs` ADD `team_id` BIGINT FK → `teams` CASCADE; index on team_id |
| V17 | `V17__user_github_login.sql` | ALTER `users` ADD `github_login` VARCHAR(255) |
| V18 | `V18__metric_snapshot_team_scope.sql` | ALTER `metric_snapshots` ADD `team_id` BIGINT FK → `teams` CASCADE; index `ix_metric_user_team_date_type` on (user_id, team_id, date, metric_type) |
| V19 | `V19__git_repo_fullname.sql` | ALTER `git_repositories` ADD `repo_full_name` VARCHAR(255); partial UNIQUE index WHERE NOT NULL |
| V20 | `V20__repo_registrations.sql` | Creates `user_repo_registrations` (id GENERATED ALWAYS AS IDENTITY, user_id FK CASCADE, repo_id FK CASCADE), UNIQUE(user_id, repo_id); index on user_id |
| V21 | `V21__github_pr_reviews.sql` | Creates `github_pr_reviews` (id GENERATED ALWAYS AS IDENTITY, pr_id FK → github_pull_requests CASCADE, reviewer_login, state, submitted_at); index on pr_id |
| V22 | `V22__cascade_delete_datasource.sql` | Adds ON DELETE CASCADE to 6 FK constraints: `git_repositories.data_source_id`, `git_commits.repository_id`, `github_pull_requests.repository_id`, `metric_snapshots.repository_id`, `issues.data_source_id`, `issues.repository_id` |
| V23 | `V23__performance_indexes.sql` | Creates 6 composite indexes: `git_commits(repository_id)`, `git_commits(repository_id, author_email, author_date)`, `github_pull_requests(repository_id)`, `github_pull_requests(repository_id, author_login, created_at)`, `github_pull_requests(repository_id, author_login, merged_at)`, `github_pr_reviews(pr_id, submitted_at)` |
| V24 | `V24__commit_sequence.sql` | ALTER SEQUENCE `git_commits_id_seq` INCREMENT BY 500 (matches Hibernate allocationSize for batch insert efficiency) |
| V25 | `V25__user_repo_registration_datasource.sql` | ALTER `user_repo_registrations` ADD `data_source_id` BIGINT FK → `data_source_configs` SET NULL |
| V26 | `V26__commit_stats_status.sql` | ALTER `git_commits` ADD `stats_status` VARCHAR(20) DEFAULT 'COMPLETE', `stats_fetched_at` TIMESTAMP, `stats_attempts` INT DEFAULT 0; partial index WHERE `stats_status = 'PENDING'` ordered by `author_date DESC` |
| V27 | `V27__pr_stats_status.sql` | ALTER `github_pull_requests` ADD `stats_status` VARCHAR(20) DEFAULT 'COMPLETE', `stats_fetched_at` TIMESTAMP, `stats_attempts` INT DEFAULT 0; partial index WHERE `stats_status = 'PENDING'` ordered by `created_at DESC` |
| V28 | `V28__metric_summaries.sql` | Creates `metric_summaries` (id, user_id FK, period_from, period_to, scope, repo_name, overview, insights, recommendations, model_name, raw_model_output, created_at) |
| V29 | `V29__jira_project_key.sql` | ALTER `data_source_configs` ADD `project_key` VARCHAR(255) — used to scope Jira collection to a specific project |
| V30 | `V30__repo_collect_issues.sql` | ALTER `git_repositories` ADD `collect_issues` BOOLEAN DEFAULT FALSE — enables issue collection alongside commits/PRs for GITHUB-type sources |
| V31 | `V31__user_avatar.sql` | ALTER `users` ADD `avatar_data` BYTEA, `avatar_content_type` VARCHAR(32), `avatar_preset` VARCHAR(64) — supports uploaded + preset avatars |
| V32 | `V32__jira_projects.sql` | Create `jira_projects` + `user_project_registrations`; backfill from `data_source_configs.project_key`; add `jira_project_id` to `issues`; replace single unique constraint on issues with two partial indexes; create `jira_project_repo_mappings`; drop `project_key` from `data_source_configs` |
| V33 | `V33__remove_github_issues_type.sql` | Eliminate `GITHUB_ISSUES` DataSourceType; migrate affected repos to `collect_issues = TRUE`; drop GITHUB_ISSUES datasource rows; GitHub issue collection is now driven exclusively by `git_repositories.collect_issues` |
| V34 | `V34__drop_registration_datasource_id.sql` | DROP `user_repo_registrations.data_source_id` (denormalized column removed; relationship derivable via `→ git_repositories → data_source_configs`) |
| V35 | `V35__datasource_constraints.sql` | Add CHECK constraints `chk_gitlocal_path` and `chk_remote_baseurl` to enforce type-specific NOT NULL rules at the DB level |
| V36 | `V36__repo_type_discriminator.sql` | Add `repo_type` VARCHAR(16) discriminator to `git_repositories` with DEFAULT backfill; add CHECK constraints `chk_repo_local_path` and `chk_repo_github_fullname` (ADR-003) |
| V37 | `V37__issues_source_column.sql` | ALTER `issues` ADD `source` VARCHAR(16) DEFAULT 'GITHUB'; rename `repo_name` → `source_context` to remove GitHub-specific naming (ADR-005 T4.2) |
| V38 | `V38__user_accessible_repos_view.sql` | Create `user_accessible_repos` view consolidating three access paths: OWNED (user created datasource), SUBSCRIBED (via `user_repo_registrations`), TEAM (via team datasource membership) — replaces three-branch service logic (ADR-004 Option B) |
| V39 | `V39__jira_projects_canonical_row.sql` | Add `base_url_normalized` to `jira_projects`; backfill from datasource `base_url`; replace `uq_jira_project_ds_key (data_source_id, project_key)` with global `uq_jira_project_global (base_url_normalized, project_key)` (ADR-002 / ADR-004) |
| V40 | `V40__metric_snapshot_docs.sql` | Comment-only migration — adds `pg_description` entries to document the dual-shape storage model of `metric_snapshots`; no DDL changes |
| V41 | `V41__metric_summaries_rename_repo_name.sql` | Rename `metric_summaries.repo_name` → `context_repo_name` (T5.2); old name implied GitHub-only; field stores snapshot-in-time scope label (repo, Jira project, or team) |
| V42 | `V42__normalize_fk_naming.sql` | Rename `user_repo_registrations.repo_id` → `repository_id` (T5.3); update `user_accessible_repos` view and all dependent indexes to match the standard FK naming pattern used elsewhere in the schema |
| V43 | `V43__user_notification_prefs.sql` | Create `user_notification_prefs` (user_id PK FK → users CASCADE, ai_brief BOOLEAN DEFAULT TRUE, sync_failures BOOLEAN DEFAULT TRUE, after_hours BOOLEAN DEFAULT TRUE, new_team_member BOOLEAN DEFAULT FALSE) — stores per-user notification toggle preferences |
| V44 | `V44__delete_account_cascades.sql` | Add `ON DELETE CASCADE` to all FK references to `users(id)` that were missing it; enables safe self-delete without orphaned rows (PDA-48/T3) |
| V45 | `V45__user_last_active_at.sql` | ALTER `users` ADD `last_active_at` TIMESTAMP — populated by `ActivityInterceptor` at most once per 5 min; used for `active 24h` admin KPI (PDA-49/B1) |
| V46 | `V46__last_active_at_timestamptz.sql` | Fix timezone mismatch: convert `last_active_at` from `TIMESTAMP WITHOUT TIME ZONE` to `TIMESTAMPTZ`; V45 stored PostgreSQL session-local time, but Hibernate read it as UTC, producing a future instant that made `timeAgo()` always return "just now" (PDA-49 bugfix) |

### 4.2 Entity-Relationship Overview

```
users ─────────────────1:N──▶ data_source_configs ──1:N──▶ git_repositories
  │                                 │ (team_id)                   │
  │                                 │                             ├──1:N──▶ git_commits
  ├──1:N (manager)──▶ teams ◀──N:1──┘                             ├──1:N──▶ github_pull_requests
  │                    │                                                          └──1:N──▶ github_pr_reviews
  └──M:N (members)──▶ teams                                        └──1:N──▶ issues
  │
  ├──1:N──▶ refresh_tokens
  ├──1:N──▶ password_reset_tokens
  ├──1:N──▶ metric_snapshots ◀──N:1── teams
  │                    └──────────────◀──N:1── git_repositories
  ├──1:N──▶ metric_summaries            (AI-generated weekly summaries)
  └──M:N──▶ git_repositories   (via user_repo_registrations)
```

---

## 5. REST API Reference

All secured endpoints require `Authorization: Bearer {accessToken}`.

| Domain | Base Path | Auth |
|---|---|---|
| Auth | `/api/auth` | Public |
| User profile | `/api/users/me` | Authenticated |
| Notification prefs | `/api/users/me/notifications` | Authenticated |
| User list | `/api/users` | MANAGER / ADMIN |
| Admin | `/api/admin` | ADMIN only |
| Teams | `/api/teams` | MANAGER / ADMIN |
| Data Sources | `/api/datasources` | Authenticated |
| Repos | `/api/repos` | Authenticated |
| Git Local | `/api/git/local` | Authenticated |
| GitHub | `/api/github` | Authenticated |
| Issues | `/api/issues` | Authenticated |
| Metrics (personal) | `/api/metrics` | Authenticated |
| Metrics (team) | `/api/metrics/teams/{teamId}` | MANAGER / ADMIN |
| AI Summaries | `/api/ai` | Authenticated |

Selected notable endpoints (added in PDA-46a / PDA-46b):

| Method | Path | Status | Description |
|---|---|---|---|
| DELETE | `/api/teams/{id}` | 204 / 403 / 409 | Delete team; 409 if datasources attached (B4.1) |
| GET | `/api/users/me/notifications` | 200 | Get or create notification prefs with defaults (B5.1) |
| PUT | `/api/users/me/notifications` | 200 | Update all four notification toggles (B5.1) |
| GET | `/api/admin/users?q=` | 200 | List users; optional case-insensitive email/username filter (B5.2) |
| DELETE | `/api/users/me` | 204 / 409 | Self-delete account and all associated data; 409 if last admin (PDA-48/T3) |

*(Full endpoint tables are in Section 3 per domain.)*

---

## 6. Security Architecture

### 6.1 JWT Flow

```
Login  → JwtService.generateAccessToken()  → {sub, type="access", roles, tokenVersion, iat, exp}
        RefreshTokenService.createRefreshToken() → stored in DB
Every request → JwtAuthFilter:
  extract token → validate sig → check exp → check tokenVersion == user.tokenVersion → set SecurityContext
Refresh → verifyToken() → rotate → new access token
Logout → revokeAllUserTokens() → increment tokenVersion → all existing JWTs immediately rejected
```

### 6.2 Refresh Token Rotation (Reuse Detection)

1. `verifyToken(incoming)` — if `revoked = true` → reuse detected → `revokeAllUserTokens()` (invalidates entire family).
2. If valid → mark old as `revoked = true`, set `replacedBy = new token`, issue new 7-day token.

### 6.3 RBAC Enforcement

- **Path-level** (`SecurityConfig`): `/api/admin/**` → ADMIN; `/api/teams/**` → MANAGER or ADMIN.
- **Method-level** (`@PreAuthorize`): team manager ownership checks, admin role promotion guards.

### 6.4 Idempotent Metric Upsert (IS NOT DISTINCT FROM)

```sql
SELECT * FROM metric_snapshots
WHERE user_id       = :userId
  AND team_id       IS NOT DISTINCT FROM :teamId
  AND repository_id IS NOT DISTINCT FROM :repoId
  AND date          = :date
  AND metric_type   = :metricType
  AND period_from   IS NOT DISTINCT FROM :periodFrom
  AND period_to     IS NOT DISTINCT FROM :periodTo
LIMIT 1
```

`IS NOT DISTINCT FROM` treats NULL = NULL as true, correctly matching snapshots with null dimensions.

---

## 7. Data Collection Pipeline

### 7.1 Async Collection Flow

```
POST /datasources/{id}/collect
  └─▶ DataSourceController
        └─▶ AsyncDataSourceCollectService.collectAsync()  [@Async → thread pool]
              └─▶ SyncJobTracker.start(dataSourceId)       [in-memory job state]
                    └─▶ DataSourceCollectService.collectForDataSource()
                          ├─ GIT_LOCAL      → GitLocalCollector.collectForRepository()
                          ├─ GITHUB         → GitHubCollector.collectForRepository()    [commits]
                          │                   GitHubPrCollector.collectForRepository()  [PRs]
                          │                   GitHubIssuesCollector (if repo.collectIssues) [issues]
                          ├─ GITHUB_ISSUES  → GitHubIssuesCollector.collectIssuesForRepo()
                          └─ JIRA           → JiraCollector.collectIssues()
```

Client polls `GET /datasources/{id}/collect/status` every 3 seconds. `SyncJobTracker` reports phase, item counts, elapsed seconds, and ETA (extrapolated from current throughput).

### 7.2 Two-Phase GitHub Enrichment

```
Phase A — Fast ingest (seconds)
  GitHubCommitIngestService / GitHubPullRequestCollector
  → pages all items from GitHub list endpoint (100/page)
  → saves with statsStatus=PENDING
  → returns List<entity> sorted newest-first

Phase B — Immediate enrich top 150 (seconds to minutes)
  GitHubCommitStatsEnrichmentService.enrichImmediate()   ← 2 worker threads
  GitHubPrStatsEnrichmentService.enrichImmediate()
  → fetches detail/reviews per item, sets statsStatus=COMPLETE
  → rate-limited: ~1.4 req/s, proactive throttle on X-RateLimit-Remaining < 100

Phase C — Background sweep (every 2 minutes, 50 items/run)
  CommitStatsEnrichmentScheduler
  → findRepositoryIdsWithStatsStatus(PENDING)
  → processPendingBatchForRepo() per repo
  → continues until all items are COMPLETE/FAILED/SKIPPED
```

---

## 8. Metric Calculation Engine

### 8.1 Dual-Scope Model

| Scope | team column | Repo source | Attribution |
|---|---|---|---|
| Personal | NULL | `UserRepoRegistration` | `author_email = user.email` / `author_login = user.githubLogin` |
| Team | team FK | `DataSourceConfig.team_id` | same per-author filter |

### 8.2 Read-Side Aggregation

- **Without `repoId`**: sum daily snapshots across all repos by date in-memory. Returns one point per calendar day.
- **With `repoId`**: returns per-repo snapshots directly.
- **Focus ratio**: controller computes `count(FOCUS_RATIO_DAYS_TASKS snapshots) / count(weekdays in range)` — the backend only stores active days.

### 8.3 Notable Calculation Details

| Metric | Method |
|---|---|
| `FOCUS_RATIO_DAYS_TASKS` | Saves value=1.0 for each weekday with ≥1 commit. Zero-commit days not stored. Aggregate ratio computed at read time. |
| `AFTER_HOURS_COMMIT_RATIO` | Converts `authorDate` to `ZoneId.of(user.timezone)`; counts commits where `hour < 9` or `hour >= 18` or `dayOfWeek in {SAT, SUN}`. |
| `DEEP_WORK_STREAK_DAYS` | Collects unique commit dates into `TreeSet<LocalDate>`, walks to find max consecutive run. |
| `MERGE_TO_MAIN_FREQUENCY_PER_WEEK` | Groups daily commit counts by ISO week key `"YYYY-WWnn"`, averages counts per week. |
| `KNOWLEDGE_SILO_SCORE` | `max(userCommitsInRepo / totalCommitsInRepo)` across all repos in window. |
| `PR_SIZE_COMPLEXITY_SCORE` | Per PR: `(additions + deletions) / max(commitsCount, 1)`. Groups by repo, takes sorted median. |
| `MERGE_WITHOUT_REVIEW_RATIO` | `findFirstReviewTimestampsByPrIds()` gives PRs WITH reviews. Ratio = `(merged PRs − PRs with reviews) / merged PRs`. |

---

## 9. AI Layer

### 9.1 Architecture

```
AiSummaryController
  └─▶ MetricsAiService
        ├─▶ MetricSnapshotService   (fetch snapshots for context window)
        ├─▶ buildAggregatedContext  (min/max/median/trend/2σ anomaly per metric)
        ├─▶ buildPrompt             (structured JSON system + user prompt)
        ├─▶ OllamaLlmClient         (POST /api/chat → llama3.2)
        └─▶ parseResponse           (strip code fences, deserialize JSON)
              └─▶ MetricsSummaryDto (overview, insights[], recommendations[])
                    └─▶ cached in "ai_summaries" (Caffeine)
```

### 9.2 Context Building

`MetricsAiService` uses 11 of the 19 metric types as AI context: the 6 daily-count metrics, DAILY_CHURN_RATIO, the 4 aggregate lead/review time metrics, and FOCUS_RATIO_DAYS_TASKS. For each metric it computes:

| Aggregate | How |
|---|---|
| `min` / `max` / `median` | Sorted value list |
| `total` | Sum (daily count metrics only) |
| `trendPct` | `(avg(recent half) − avg(early half)) / avg(early half) × 100` |
| `anomaly` | True if any value is > 2 standard deviations from the mean |

Aggregate metrics (lead times, review time) use an exact `periodFrom/periodTo` query; daily-count metrics use a `dateBetween` query. This distinction is enforced in `AGGREGATE_METRICS` set.

### 9.3 Ollama Configuration

| Property | Config key | Default |
|---|---|---|
| Base URL | `ai.ollama.base-url` | `http://localhost:11434` |
| Model | `ai.ollama.model` | `llama3.2` |
| Max tokens | `ai.ollama.num-predict` | `1024` |
| Seed | `ai.ollama.seed` | `42` |

### 9.4 Caching

Spring Cache (`ai_summaries`) is backed by Caffeine. Cache keys:
- Personal: `{userId, from, to, repoId}`
- Team: `{"team", teamId, from, to}`

The weekly scheduler calls `metricsAiService.generateSummary()` which hits the cache; pre-generated entries serve dashboard requests with zero Ollama latency.

---

## 10. Frontend Application

Built with React 18 + Vite + TypeScript. Built into `src/main/resources/static/` and served by Spring Boot Tomcat. Non-API routes served by `SpaFallbackController` for client-side navigation.

### 10.1 Routing

| Path | Component | Auth |
|---|---|---|
| `/login` | `LoginPage` | Public |
| `/register` | `RegisterPage` | Public |
| `/forgot-password` | `ForgotPasswordPage` | Public |
| `/welcome` | `WelcomePage` | Public (post-login redirect) |
| `/dashboard` | `DashboardPage` | Protected |
| `/team` | `TeamDashboardPage` | Protected |
| `/team-manage` | `TeamManagePage` | MANAGER / ADMIN |
| `/datasources` | `DataSourcesPage` | Protected |
| `/settings` | `SettingsPage` | Protected |
| `/admin` | `AdminPage` | ADMIN |
| `*` | — | Redirect to `/dashboard` |

### 10.2 Pages

**`LoginPage`** — Editorial split layout (`1fr 1.1fr` grid). Left column: `Logo` with wordmark, `── sign in` eyebrow, "Welcome back." heading, email-or-username + password `.input` fields, forgot-password link, `btn-accent` submit, register link, version footer. Right column (decorative — no live data): `var(--bg-inset)` background with a repeating horizontal-line texture overlay, "── what's inside" eyebrow, editorial h2, 2-col preview-card grid (sources / team / AI summary chips), DORA+SPACE footer badges. Below 880 px: collapses to single column, right column hidden. Auth logic unchanged: 401/403 error in `var(--coral)`, server-unreachable fallback.

**`RegisterPage`** — Single-column, same left-column treatment as LoginPage (Logo top, `── create account` eyebrow, form middle, version footer). Fields: username, email, password (min 6 characters). On success → `/login`.

**`ForgotPasswordPage`** — Single-column, left-column treatment (Logo top, `── reset password` eyebrow, form, version footer). Email input → `POST /auth/forgot-password`. Success state renders a `.card` with `── link sent` eyebrow in emerald and instructions.

**`WelcomePage`** — Post-login splash. Background: `radial-gradient(ellipse at top, var(--violet-bg) 0%, var(--bg) 60%)`. `Logo` with `wel-float` keyframe animation (translateY 0 → −8 px → 0, 2.4 s). "── welcome back" eyebrow, "Hi, {firstName}." heading with firstName in `var(--violet-strong)`, "Spinning up your workspace." body. Step list with check / pulse-dot / idle-dot indicators cycling at 900 ms. Three progress dots with pulse on active step. Skip button. Auto-redirects to `/dashboard` after 3.5 s (bumped from 2.8 s in PDA-47/T9.2 to match animation timing).

**`DashboardPage`** — Personal metrics. Editorial layout. Date range via `TopBar` (`useDateRange()`). Recalculate via `da:recalculate` CustomEvent.
- **Hero block**: `t-h1` headline interpolates live totals (commits, PRs merged, deep-work streak); appended with `aiSummary.headline` when a summary is available. `t-body` overview paragraph from `aiSummary.overview` (placeholder when none).
- **Commits hero card**: total count + `<Sparkline>` of daily commit activity.
- **Velocity group** (`hr-label velocity`): 8 `<KpiTile>` in 2 rows of 4 with a `.divider` between rows — commits, prs merged, pr lead time, focus ratio, prs created, issues closed, review response, 1st commit→merge.
- **Wellness · Quality group** (`hr-label wellness · quality`): 8 `<KpiTile>` in 2 rows of 4 — after-hours, refactor ratio, merge w/o review, merge frequency, deep work streak, avg churn, knowledge silo, pr size · median.
- **AI Summary** (`hr-label ai summary`): `<AiSummaryCard>` for the selected date range; passes summary up via `onSummaryGenerated` to drive the hero block.
- **Activity over time** (`hr-label activity · over time`): full-width commits bar chart; `.charts-grid` with PR flow (created/merged sparklines) and Code churn sparkline; Issues card (closed + created bar charts, shown only when data present).

**`DataSourcesPage`** — Add/list/delete datasources. Editorial layout with `.card` per source. Sync progress display with real-time polling every 3 s.
- **Hero**: `N sources, M repos feed the metrics.` with a `Connect source` button that expands an in-page collapsible add form.
- **Add form**: 3-button type picker (GitHub / Jira / Local Git with accent highlight on selected), `.input` fields for name, base URL, path, API token (eye-toggle show/hide), team assignment, repo full name, Jira project key.
- **Sources list**: each source in a `<div className="card">` with type chip (`Chip` component), sync status using editorial `.dot-live / .dot-warn / .dot-fail`, play/delete `.btn-icon` buttons, expand-panel for repos.
- **Repos panel**: subscribe/unsubscribe per repo, external link, collect-issues toggle. Jira panel shows tracked projects with subscribe/unsubscribe.

**`TeamDashboardPage`** — Team selector dropdown (hidden when only one team). Team KPI strip: 4 `KpiTile` (team commits, PRs merged, issues closed, active members) each with a descriptive `tooltip`. `MultiLineChart` for per-member daily commits. Member summary table with per-row click → `MemberDetailModal`. `AiTeamInsightCard` shows AI-generated team summary. Recalculate wired via `da:recalculate` CustomEvent; `onError` surfaces backend errors in a coral banner; `onSuccess` calls `qc.invalidateQueries()` (no-arg) to refetch all active queries.

**`MemberDetailModal`** (inner component of `TeamDashboardPage`) — `Modal` (620 px). Title: `{username} — {formatDate(from)} – {formatDate(to)}` (concrete date range). Header shows `<span class="dot dot-live" />active {timeAgo(lastActiveAt)}` if `lastActiveAt` is present, otherwise "no activity recorded". 3 × 2 KPI grid with 6 metrics; each icon wrapped in `Tooltip` showing metric description. Daily commits rendered as `MetricBarChart` (last 60 data points, `var(--violet)` bars, 140 px height). `MemberSummaryDto` carries `lastActiveAt?: string` (ISO timestamp) populated by `MetricsController.getTeamSummary` from `User.lastActiveAt`.

**`TeamManagePage`** — Create team form. Team cards with expandable member list. Add-member modal (searches all users, excludes existing members). Remove member with confirmation. `activeTeam` is derived reactively from the `teams` query result using a stored `activeTeamId` pointer, so the member list updates immediately after add/remove mutations complete without requiring a modal close/reopen.

**`SettingsPage`** — Editorial `.page.narrow` layout. **Appearance card**: theme toggle (light/dark), `AccentSwatches` + hex input, live preview strip, **logo picker** (T8.2 — 4 clickable cards calling `setTheme({ logo })` from `useTheme()`; "reset to default" reverts to `ACTIVE_LOGO`). **Avatar card**: upload via `avatarApi.upload`, preset grid via `avatarApi.setPreset`, remove via `avatarApi.delete`. **Profile card**: username, email, GitHub login, timezone picker with `Chip(emerald, dot) "auto"` badge when tz matches browser tz, role chip. Save → `PUT /users/me`. **Security card**: session JWT info, collapsible password change form. **Notifications card**: four toggle switches wired to `GET/PUT /api/users/me/notifications` (B5.1); toggles fire `notifMutation` on change and update React Query cache optimistically. **Danger zone**: "delete account" button opens a confirmation `Modal` requiring the user to type their email exactly; on confirm calls `DELETE /api/users/me`, then `logout()`, then redirects to `/login`; 409 last-admin guard surfaces as an inline error in the modal (PDA-48/T3).

**`AdminPage`** — ADMIN only (redirects to `/dashboard` if not admin). Hero `N users` headline. KPI strip: `users` count (from `/admin/users`); `active 24h`, `db size` (formatted as MB), `ai calls today` — all live from `GET /admin/stats` via `useQuery(['admin-stats'])`. Users table: email, role dropdown (`RoleDropdown` — inline `useMutation` per row), delete button. **Invite modal**: visual placeholder, `TODO(admin-invites-backend)`. **Promote-to-admin modal**: search input calls `GET /admin/users?q=` (B5.2) with `enabled: adminOpen`; results list shows non-admin users; selecting one highlights it; "promote" button calls `PUT /admin/users/{id}/role` with ADMIN. Current user cannot be deleted.

### 10.3 Components

**`AppShell`** — Auth guard (`useAuth().user` → redirect to `/login` if null). Composes `Sidebar`, `TopBar`, `<Outlet>`, and `StatusBar` in a full-height flex layout. Listens for `da:open-palette` CustomEvent and global `Ctrl+K` / `⌘K` keydown to open `CommandPalette` (PDA-48/T1).

**`CommandPalette`** (`src/components/ui/CommandPalette.tsx`) — Keyboard-navigable command palette rendered as an overlay (not a `Modal`). Opens on `da:open-palette` event or `Ctrl+K`/`⌘K`. Commands list (8 entries): Go to Dashboard, Go to Team (MANAGER+), Go to Manage Teams (MANAGER+), Go to Data Sources, Go to Settings, Go to Admin (ADMIN), Toggle dark mode, Open date range picker. Text input filters by `label.toLowerCase().includes(query)`. `↑`/`↓` moves selection (accent left border on selected row); `Enter` fires the action; `Escape` closes (PDA-48/T1).

**`Sidebar`** — Fixed 240 px (`--sidebar-w`). Brand block: `Logo` + `APP_VERSION` pill + `.dot-live` eyebrow. `⌘K` search button dispatches `da:open-palette`. Workspace nav: Personal (`/dashboard`), Team (`/team`, MANAGER+), Manage (`/team-manage`, MANAGER+), Sources (`/datasources`). Account nav: Settings, Admin (ADMIN+). Active item: `var(--bg-2)` background + 2 px accent left-bar. User card footer: Avatar + **username** + role badge → navigates `/settings`. Theme toggle and logout buttons.

**`TopBar`** — Sticky 56 px header. Breadcrumbs derived from `useLocation().pathname` via a static route map. Date-range button opens `DateRangeModal` and reads/writes `useDateRange()`. Recalculate button dispatches `da:recalculate` CustomEvent (per-page listeners wire the mutation in PR3+).

**`StatusBar`** — Vim-style footer bar (`.statusbar` CSS class). Dynamic: online/offline indicator (`navigator.onLine` + events), datasource count and last-sync time from `useQuery(['datasources'])`, current view from `useLocation()`, browser timezone via `Intl.DateTimeFormat`, build version. Hidden when `useTheme().showStatusBar === false`.

**`DateRangePicker`** — Dropdown with preset ranges (last 7/30/90 days) and custom from/to inputs. Retained for components that haven't been migrated to `DateRangeModal`.

**`DateRangeModal`** — Modal-based range picker used by `TopBar`. Preset grid (Today, Yesterday, Last 7/14 days, 4/8 weeks, Last quarter, Year to date, Custom) + from/to date inputs.

**`KpiTile`** — Editorial metric tile. Props: `label`, `value`, `sub?`, `icon?`, `tooltip?`, `accent?`, `size?`, `emphasis?`. When both `icon` and `tooltip` are provided, the icon is wrapped in `Tooltip`. Used in all metric grids across Dashboard, TeamDashboard, and AdminPage. Replaces the removed `KpiCard`.

**`Sparkline`** — SVG sparkline (no Recharts). `vectorEffect="non-scaling-stroke"`. Props: `data`, `color`, `height`, `responsive`.

**`Chip`** — Status/filter pill. Colors: `violet | cyan | amber | emerald | coral` + default. Optional `dot` prop adds a `.chip-dot` indicator.

**`SectionHead`** — Eyebrow + `t-h2` title + optional count badge + optional `action` slot.

**`Tooltip`** — Hover popover. Wraps children; shows `content` above the hovered element on mouse-enter. Renders via `ReactDOM.createPortal` into `document.body` with `position: fixed` and `z-index: 9999` — safe inside `overflow: hidden` ancestors such as the `Modal` dialog wrapper.

**`Modal`** — Focus-trapped overlay. Escape closes, backdrop closes, body scroll locked. `@keyframes mfade` / `mpop` injected inline. Props: `open`, `onClose`, `eyebrow?`, `title`, `width?`, `footer?`.

**`ProseWithNumbers`** (`src/components/ui/ProseWithNumbers.tsx`) — Renders a paragraph with numeric tokens wrapped in `<strong>` using `var(--font-mono)` so numbers stand out editorially from prose. Backed by `tokeniseNumbers` from `src/lib/prose.ts` which splits text on the pattern `\d[\d,.]*(×|%|h|d|\/wk)?`. Used in `AiSummaryCard` overview (PDA-48/T2).

**`AccentSwatches`** — Row of clickable colour swatches (the `ACCENT_PRESETS` list). `onChange` fires once per valid 6-char hex, including manual hex input.

**`AccentPicker`** — Extended accent selector combining `AccentSwatches` with a hex text input and a live preview square.

**`Logo`** — Brand mark dispatcher. `variant` prop selects from `{ pulse, bracket, slash, crystal }`. Props: `size?`, `withWordmark?`. Falls back to `LogoPulse` for unknown variants. Used in `Sidebar`, `LoginPage`, `RegisterPage`, `ForgotPasswordPage`, `WelcomePage`.

**`@/components/icons`** — 22 custom metric + brand SVG icons (16 × 16 px, `viewBox="0 0 24 24"`, `stroke="currentColor"`): `Commits`, `PRMerged`, `PRCreated`, `LeadTime`, `Focus`, `Review`, `FirstCommit`, `IssuesCreated`, `IssuesClosed`, `IssueLead`, `Churn`, `DeepWork`, `AfterHours`, `Refactor`, `NoReview`, `MergeFreq`, `Silo`, `PRSize`, `AI`, `Branch`, `Jira`, `Folder`. Rule: `lucide-react` for nav/chrome/generic actions; `@/components/icons` for metric and brand glyphs.

**`MetricLineChart`** / **`MetricBarChart`** — Recharts wrappers. Responsive, date-sorted x-axis, configurable color/label/unit/height. `MetricBarChart` tooltip popup and axis ticks use CSS custom properties (`var(--bg-card)`, `var(--line)`, `var(--fg)`, `var(--fg-3)`, `var(--line-2)`) for full light/dark theme compatibility. Column hover cursor uses `var(--bg-2)`.

**`MultiLineChart`** — Multi-series for team data. Pivots by (date, username). 7-color palette.

**`AiSummaryCard`** — Fetches personal AI summary for the current date range. Editorial layout: `AI SUMMARY` eyebrow, italic `t-h2` headline, `Fresh / Outdated` chip, copy button, Regenerate button. Insight rows use `+/!/~` symbols coloured by `insight.kind` (`positive → emerald`, `risk → coral`, `note → amber`) with a `Chip` for the linked metric name. Footer: `Shield` icon + "Generated locally" + model name + `timeAgo`. "Ask follow-up" button disabled with `TODO(ai-follow-up):` tooltip. `onSummaryGenerated` callback drives the Dashboard hero block.

**`AiMetricExplainDrawer`** — Slide-in panel. Receives `metricLabel`, `metricDescription`, `metricValue`, and the current `MetricsSummaryDto`. Shows metric description, current value, and up to 3 matching AI insights from the summary.

**`AiTeamInsightCard`** — Team equivalent of `AiSummaryCard`. Shows team overview + per-member insights.

**UI primitives (retained)**: `Avatar` (image / preset / initials fallback, deterministic colour), `Button` (primary/secondary/ghost/danger, sm/md/lg, loading spinner), `Input` (forwardRef, label, error), `Select` (forwardRef, label, options), `Badge` (6 colors, used for role pills in dropdowns), `Spinner` / `PageSpinner`.

### 10.4 State & Data Fetching

**`AuthContext`** — `user`, `isLoading`, `login()`, `register()`, `logout()`, `isManager`, `isAdmin`. Initializes from `GET /users/me` on mount. `login()` clears React Query cache before setting tokens. `logout()` clears tokens and cache.

**`lib/api.ts`** — Axios instance with `baseURL='/api'`. Request interceptor attaches `Bearer` token. Response interceptor auto-refreshes on 401, retries original request; on refresh failure clears tokens and redirects to `/login`.

**React Query** — `staleTime: 2 min`, `retry: 1`. Recalculate mutation invalidates all queries via `qc.invalidateQueries()` (no-arg form — unambiguously matches all keys).

### 10.5 API Modules

| Module | Endpoints covered |
|---|---|
| `api/metrics.ts` | All 20 personal metric endpoints + 5 team metric endpoints |
| `api/datasources.ts` | CRUD + collect + status polling; defines `SyncStatus` interface |
| `api/repos.ts` | List, subscribe, unsubscribe |
| `api/teams.ts` | CRUD, add/remove member, rename, delete |
| `api/ai.ts` | `/summary`, `/team/{id}/summary`, `/explain`, `/summaries/latest` |
| `api/issues.ts` | Issue listing |
| `api/users.ts` | Profile update, avatar, `notifications.get()`, `notifications.update(dto)` |

---

### 10.6 Theme System

The editorial design system lives in `frontend/src/index.css` (`@theme` block removed; `@import "tailwindcss"` retained for utility classes).

- **Palette** — warm-paper light mode and carbon dark mode. All colours are CSS custom properties: `--bg`, `--bg-2`, `--bg-inset`, `--bg-card`, `--fg`, `--fg-2`, `--fg-3`, `--muted`, `--line`, `--line-2`. Five OKLCH accent triples: `--accent`, `--accent-strong`, `--accent-bg` (user-configurable); and five named colour tokens: `--violet`, `--cyan`, `--amber`, `--emerald`, `--coral` each with `-strong` and `-bg` variants.
- **Dark mode** — `html[data-theme="dark"]` overrides (set by `ThemeProvider`). No class-based toggling.
- **Typography** — Font stack: Geist (body/UI, `--font-sans`), IBM Plex Mono (display numbers and headings, `--font-mono`/`--font-serif`). IBM Plex Mono is the settled design choice; `html[data-mono]` and `html[data-numerals]` switcher variants were design-canvas explorations and are not in the app. Fonts loaded from Google Fonts in `index.html`.
- **`useTheme()`** — Context hook exported from `src/context/ThemeContext.tsx`. State shape: `{ theme: 'light' | 'dark', logo: LogoVariant, accent: string, showStatusBar: boolean }`. `setTheme(partial)` merges the partial, writes `document.documentElement.dataset.theme`, updates `--accent` / `--accent-strong` / `--accent-bg` inline on `:root`, and persists to `localStorage['da-theme-v1']`.
- **Accent CSS variables** are computed via `color-mix(in oklab, accent 72%, black|white)` to derive `--accent-strong` and `--accent-bg`.

### 10.7 Brand

- **Four logo variants**: `pulse` (chart-as-D bars, default), `bracket` (`[d]` monogram with cursor), `slash` (terminal `~/d` with blinking cursor), `crystal` (editorial serif italic D in a circle). Each lives in `src/components/brand/` as a standalone React component.
- **`ACTIVE_LOGO`** in `src/config/branding.ts` is the single line that sets the shipping default across the entire app. Changing it and clearing `localStorage` flips every surface — sidebar, login, welcome, favicons — with no other file changes.
- **Logo picker in Settings → Appearance**: four clickable cards calling `setTheme({ logo: variant })`. Persists to `localStorage`. "Reset to default" calls `setTheme({ logo: ACTIVE_LOGO })`.
- **Favicons**: `public/favicons/{pulse,bracket,slash,crystal}.svg`. A `useEffect` in `App.tsx` swaps `<link rel="icon">` whenever `theme.logo` changes.

---

## 11. Scheduled Tasks

| Scheduler | Schedule | What it does |
|---|---|---|
| `TokenCleanupScheduler` | Daily 02:00 UTC | Deletes expired `refresh_tokens`; deletes used or expired `password_reset_tokens` |
| `MetricsScheduler` | Daily 01:00 UTC | For every user: `calculateDailyMetrics(userId, yesterday, yesterday)`. Per-user failures logged, do not abort run |
| `MetricsSummaryScheduler` | Every Monday 08:00 UTC | Generates a personal AI summary for every user for the previous week (Mon–Sun); persists to `metric_summaries` |
| `CommitStatsEnrichmentScheduler` | Every 2 minutes | Finds repos with PENDING commits or PRs; processes up to 50 per repo per run. Respects GitHub rate limits. Continues until all enriched |

---

## 12. Technology Stack

### Backend

| Technology | Version | Role |
|---|---|---|
| Java | 17 | Language |
| Spring Boot | 3.5.10 | Framework, embedded Tomcat |
| Spring Data JPA / Hibernate | (managed) | ORM |
| Spring Security | (managed) | JWT filter, RBAC |
| Spring Cache / Caffeine | (managed) | AI summary caching (`ai_summaries`) |
| JJWT | 0.13.0 | HS256 token generation/validation |
| PostgreSQL | 16 | Primary data store |
| Flyway | (managed) | Schema versioning (43 migrations) |
| JGit | 7.2.1.202505142326-r | Local Git repository reading |
| Kohsuke GitHub API | 2.0-rc.5 | GitHub REST API client |
| Spring RestTemplate | (managed) | Jira API + Ollama HTTP calls |
| Spring Mail / JavaMailSender | (managed) | SMTP email |
| Ollama | external | Local LLM inference (llama3.2) |
| Lombok | 1.18.34 | `@Data`, `@Builder`, `@RequiredArgsConstructor`, etc. |
| Swagger Annotations | 2.1.7 | OpenAPI annotations |
| Docker / Docker Compose | — | Local PostgreSQL |
| Maven Wrapper | 3.x | Build |

### Frontend

| Technology | Version | Role |
|---|---|---|
| React | 18 | UI framework |
| TypeScript | 5.x | Type safety |
| Vite | 5.4.x | Build + dev server |
| React Router | 6.x | Client-side routing |
| TanStack React Query | 5.x | Server state, caching |
| Axios | 1.x | HTTP client + JWT interceptors |
| Recharts | 2.x | Charts (bar, line, responsive) |
| Tailwind CSS | 3.x | Utility-first styling |
| Lucide React | — | Icons |
| clsx | — | Conditional classNames |

---

## 13. Configuration & Deployment

### 13.1 Environment Variables

| Variable | Default | Required | Description |
|---|---|---|---|
| `JWT_SECRET` | (dev hex value) | **Yes in prod** | 256-bit hex; HMAC-SHA256 signing key |
| `APP_BASE_URL` | `http://localhost:8080` | No | Used in password reset email links |
| `SMTP_HOST` | `smtp.gmail.com` | No | SMTP hostname |
| `SMTP_PORT` | `587` | No | SMTP port (STARTTLS) |
| `SMTP_USERNAME` | — | Yes (for email) | SMTP auth username |
| `SMTP_PASSWORD` | — | Yes (for email) | SMTP app password |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | No | Ollama server base URL |
| `OLLAMA_MODEL` | `llama3.2` | No | LLM model name |
| `OLLAMA_NUM_PREDICT` | `1024` | No | Max tokens per completion |
| `OLLAMA_SEED` | `42` | No | Determinism seed |

### 13.2 application.yml Summary

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/dev_analytics
    username: postgres
    password: 123
  jpa:
    hibernate.ddl-auto: validate           # Flyway owns the schema
    properties.hibernate.jdbc.batch_size: 500
    properties.hibernate.order_inserts: true
    properties.hibernate.order_updates: true
  flyway.enabled: true
  mail:
    host: ${SMTP_HOST:smtp.gmail.com}
    port: ${SMTP_PORT:587}
    properties.mail.smtp.starttls.enable: true

app:
  jwt:
    access-expiration:  900000             # 15 minutes
    refresh-expiration: 604800000          # 7 days
  password-reset.expiration: 3600000      # 1 hour
  base-url: ${APP_BASE_URL:http://localhost:8080}

ai:
  ollama:
    base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
    model: ${OLLAMA_MODEL:llama3.2}
    num-predict: ${OLLAMA_NUM_PREDICT:1024}
    seed: ${OLLAMA_SEED:42}

server.port: 8080
logging.level.root: INFO
```

### 13.3 Build & Run

```bash
# Start PostgreSQL
docker-compose up -d

# Run backend (from dev-analytics/)
./mvnw spring-boot:run

# Build frontend (embeds into src/main/resources/static/)
cd frontend && npm run build

# Build fat JAR
./mvnw clean package

# Run JAR
java -DJWT_SECRET=<hex> -DSMTP_PASSWORD=<pass> \
     -jar target/dev-analytics-0.0.1-SNAPSHOT.jar
```

Health: `GET /actuator/health` (no auth required). App: `http://localhost:8080`.

Ollama must be running separately: `ollama serve` (and `ollama pull llama3.2` on first run).

---

*Personal Developer Analytics — multi-source data collection (GitHub, Jira, local Git), two-phase async enrichment, 19-metric calculation engine with personal/team scope isolation, stateless JWT auth with token-version logout invalidation, RBAC, local LLM AI insights (Ollama llama3.2) with weekly scheduled summaries, and a full React SPA served from the same Spring Boot process.*
