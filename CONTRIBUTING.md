# Contributing to Personal Developer Analytics

Thank you for contributing to Personal Developer Analytics. This project is a self-hosted developer analytics platform for a master's thesis that aggregates telemetry from local Git repositories, GitHub, GitLab, and Jira, and presents metrics and local AI-generated insights through a React dashboard [cite:8][cite:9].

## Before contributing

Please read `README.md`, `PROJECT_DESCRIPTION.md`, and the repository's `CODE_OF_CONDUCT.md` before opening a pull request. The current system combines a Spring Boot backend, a React 18 frontend, PostgreSQL persistence, Flyway migrations, and local Ollama-based AI features, so even small changes can cross architecture, security, and data-model boundaries [cite:8][cite:9].

For feature work, refactoring, schema changes, or anything that affects architecture, open an issue or discussion first. This is especially important because the project is part of a thesis and values traceability, rationale, and clear scope boundaries alongside working software [cite:4][cite:5].

## Contribution goals

Good contributions improve one or more of the following:

- Correctness of data collection from Git, GitHub, GitLab, or Jira [cite:8][cite:9].
- Quality and validity of the metrics pipeline and repository- or team-scoped analytics behavior [cite:4][cite:9].
- Security, privacy, and governance properties of the self-hosted platform [cite:4][cite:9].
- Usability of the dashboard, team workflows, messaging, or AI-assisted summary features [cite:9].
- Documentation, reproducibility, and maintainability expected from a master's thesis artifact [cite:4][cite:5].

## Development principles

Contributions should preserve the project's architectural direction [cite:9]. In practice:

- Respect strict Controller-Service-Repository layering; controllers should not access repositories directly [cite:9].
- Keep backend code domain-oriented and cohesive across packages such as `auth`, `datasource`, `git`, `github`, `jira`, `metrics`, `ai`, `messaging`, `notification`, and `security` [cite:9].
- Preserve privacy-aware design choices, because the thesis explicitly positions the system as a self-hosted alternative to cloud analytics tools [cite:4][cite:9].
- Prefer small, reviewable pull requests instead of large mixed changes.
- Document architectural trade-offs when changing security, data ownership, synchronization, or metric semantics [cite:5][cite:9].
- Update code, tests, and documentation together.

## Project overview

The current platform includes a Spring Boot API, a React SPA, JWT-based authentication, RBAC, asynchronous data-source collection, a metrics engine with 19 metrics, weekly AI summaries, and several newer collaboration-oriented features such as direct messaging, team invitations, notifications, Jira-project mapping, avatars, and persistent sync job tracking [cite:9]. The documented stack includes Java 17, Spring Boot 3.5, PostgreSQL 16, Flyway, React 18, TypeScript, Vite, Tailwind v4, Recharts, Docker Compose, and local Ollama with `llama3.2` [cite:8][cite:9].

## Main areas to contribute

| Area | What it covers |
|---|---|
| Backend core | Authentication, users, teams, security, notifications, invitations, messaging, and rate limiting [cite:9] |
| Data ingestion | Local Git collection, GitHub collection, Jira project discovery and mapping, incremental sync, and enrichment phases [cite:9] |
| Metrics | Snapshot generation, aggregate queries, repo/team scoping, anomaly handling, and metric validity [cite:4][cite:9] |
| AI features | Summary generation, cached summaries, follow-up AI conversations, and local Ollama integration [cite:8][cite:9] |
| Frontend | Dashboard, team dashboard, data sources, messages, admin, settings, repo filters, charts, and editorial UI components [cite:9] |
| Infrastructure | Docker Compose setup, env configuration, secrets handling, and build packaging [cite:8][cite:9] |

## Local setup

The documented local development flow uses PostgreSQL, the Spring Boot backend, the Vite frontend dev server, and a separate Ollama process for AI features [cite:8]. Prerequisites include Java 17, Node 20, PostgreSQL 16 or Docker, Ollama, and `openssl` for generating secrets [cite:8].

Typical development setup:

1. Start PostgreSQL, for example with `docker compose up -d db` [cite:8].
2. Start Ollama with `ollama serve`, and pull `llama3.2` on first use [cite:8].
3. Run the backend with `./mvnw spring-boot:run` [cite:8].
4. Run the frontend from `frontend/` with `npm install` and `npm run dev` [cite:8].
5. Configure environment variables such as `JWTSECRET`, `ENCRYPTIONKEY`, and `POSTGRESPASSWORD`; never commit real secrets [cite:8][cite:9].

## Branches and commits

Use short-lived branches named by intent, for example:

- `feature/jira-project-linking`
- `fix/team-summary-cache-key`
- `refactor/token-encryption-service`
- `docs/repo-scope-behavior`

Write commit messages in the imperative mood and keep them specific. Good examples are `Add repo filter support to team metric endpoints` and `Fix AES-GCM legacy token migration handling`.

## Backend guidelines

Backend contributions should align with the documented architecture and stack: Java 17, Spring Boot, Spring Security, Spring Data JPA, PostgreSQL, and Flyway [cite:8][cite:9]. Keep business logic in services, keep controllers thin, and avoid leaking persistence concerns across layers [cite:9].

When changing ingestion or synchronization logic, account for the existing async collection model, the staged GitHub enrichment pipeline, and the persistent sync-job state now stored in the database [cite:9]. When changing repository ownership or subscription logic, preserve the documented canonical-row model for upstream repositories and the access rules built around `UserRepoRegistration` [cite:9].

Security-sensitive areas require extra care. The system uses JWT access tokens, rotating refresh tokens, RBAC, AES-256-GCM token encryption, per-user rate limiting, and password reset flows, so changes in these areas must be conservative, well-tested, and clearly explained [cite:9].

## Frontend guidelines

Frontend changes should fit the current React 18, TypeScript, Vite, Tailwind v4, and Recharts setup [cite:8]. The UI has evolved toward an editorial layout system with reusable components such as `KpiTile`, `Sparkline`, `RepoSelector`, `DateRangeModal`, and context-driven dashboard state, so new features should reuse existing patterns instead of introducing parallel UI abstractions [cite:9].

If you change user-facing workflows, include screenshots or short recordings in the pull request. This is especially useful for dashboards, team views, messaging, data-source configuration, repo filters, and AI-summary interactions.

## Database and migrations

All schema changes must go through Flyway migrations because the project already has an extensive migration history and explicitly documents database evolution as part of the artifact [cite:9]. Migration names should be descriptive, changes should be scoped narrowly, and application code should remain compatible with the new schema by the time the branch is merged [cite:9].

When touching core entities such as repositories, metrics, issues, summaries, teams, messages, or invitations, update relevant documentation as well. Data-model invariants are part of the thesis argumentation and should not change accidentally [cite:4][cite:9].

## Testing and verification

Before opening a pull request, verify the change locally using the documented commands where applicable [cite:8]. At minimum, contributors should run the relevant build, test, and lint steps for the areas they changed.

Recommended checklist:

- Backend tests: `./mvnw test` [cite:8].
- Frontend tests: `npm run test:run` from `frontend/` [cite:8].
- Frontend linting: `npm run lint` from `frontend/` [cite:8].
- Manual verification of affected UI and API flows.
- Validation of role-sensitive behavior when changing auth, team, admin, or messaging features [cite:9].
- Validation of metric behavior with controlled examples when changing calculations, repo scoping, or summary generation, because metric validity is one of the thesis research questions [cite:4][cite:9].

If your change affects setup or deployment, also verify the documented local or Docker-based startup path [cite:8]. If your change affects demo behavior, verify the documented demo profile still works as expected [cite:8].

## Pull request expectations

Each pull request should include:

- A concise problem statement.
- The scope of the solution.
- A note on affected modules, packages, or migrations.
- Testing and verification notes.
- Screenshots for UI changes.
- Any architectural or data-model trade-offs that reviewers should understand.

For non-trivial changes, explain why the chosen approach was selected over alternatives. This helps maintain thesis-grade traceability and aligns with the grading emphasis on methodological clarity, solution rationale, and evaluation quality [cite:5].

## Documentation expectations

Documentation is part of the deliverable quality, not an afterthought [cite:5]. Update the relevant repository files when your change affects:

- API behavior or endpoint semantics.
- Authentication, authorization, or token handling.
- Metric definitions or aggregation behavior.
- Data-source onboarding or sync workflow.
- Environment variables, setup, or deployment.
- AI features, conversations, caching, or local model assumptions.
- Team workflows, invitations, messages, or notifications.

## Security and privacy

This project is explicitly framed as a privacy-aware, self-hosted analytics system, so contributions must not weaken that positioning [cite:4][cite:9]. Never commit secrets, real API tokens, database credentials, personal data dumps, or proprietary datasets [cite:8][cite:9].

Prefer minimal data access, minimal permission scope, and explicit handling of sensitive values. If a change affects encryption, authentication, user data exposure, or notification behavior, call that out clearly in the pull request [cite:9].

## Reporting issues

Use GitHub issues for bugs, enhancements, and documentation tasks. For security vulnerabilities or Code of Conduct concerns, use the private contact path defined in `SECURITY.md` or `CODE_OF_CONDUCT.md` instead of posting publicly.

## Acceptance standard

A contribution is most likely to be accepted when it is technically correct, scoped well, easy to review, aligned with the documented architecture, and supported by updated tests or verification notes [cite:5][cite:9]. Clean code, explicit reasoning, and consistency with the thesis goals matter more than adding a large volume of changes [cite:4][cite:5].
