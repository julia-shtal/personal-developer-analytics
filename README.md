# Personal Developer Analytics

<!-- CI / build -->
![CI](https://github.com/julia-shtal/personal-developer-analytics/actions/workflows/ci.yml/badge.svg?branch=dev)
![Release](https://img.shields.io/github/v/release/julia-shtal/personal-developer-analytics)

<!-- Backend stack -->
![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.10-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Flyway migrations](https://img.shields.io/github/directory-file-count/julia-shtal/personal-developer-analytics/dev-analytics%2Fsrc%2Fmain%2Fresources%2Fdb%2Fmigration?type=file&label=Flyway%20migrations&logo=flyway&logoColor=white&color=CC0200)

<!-- Frontend stack -->
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-6.x-3178C6?logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-5.4-646CFF?logo=vite&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind-4.x-06B6D4?logo=tailwindcss&logoColor=white)

<!-- AI / infra -->
![Ollama](https://img.shields.io/badge/Ollama-llama3.2-000000?logo=ollama&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)

<!-- Repo activity -->
![Last commit](https://img.shields.io/github/last-commit/julia-shtal/personal-developer-analytics/main)
![Open issues](https://img.shields.io/github/issues/julia-shtal/personal-developer-analytics)
![License](https://img.shields.io/github/license/julia-shtal/personal-developer-analytics)

Master's thesis project — a self-hosted platform that aggregates developer productivity
metrics from local Git repositories, GitHub, and Jira, and surfaces them through
a React dashboard with AI-generated insights via local Ollama.

## Architecture

| Layer | Technology |
|-------|-----------|
| Backend API | Spring Boot 3.5, Java 17, JWT auth |
| Database | PostgreSQL 16, Flyway migrations |
| Frontend SPA | React 19, TypeScript, Vite, Tailwind CSS 4, Recharts |
| AI insights | Ollama (llama3.2) — runs locally, no cloud dependency |
| Mail (dev/Docker) | MailHog — local SMTP capture, web UI on port 8025 |
| Deployment | Docker Compose (app + Postgres + Ollama + MailHog) |

## Prerequisites

- Java 17+
- Node 20+
- PostgreSQL 16 (or Docker)
- [Ollama](https://ollama.com) — for AI features
- `openssl` — for generating secrets
- Docker + Docker Compose — for one-command deployment

## Environment Variables

| Variable | Purpose | Default | How to generate |
|----------|---------|---------|----------------|
| `JWT_SECRET` | JWT signing key | none — **startup fails** if unset | `openssl rand -hex 32` |
| `ENCRYPTION_KEY` | AES-256-GCM token encryption key (base64) | none — **startup fails** if unset | `openssl rand -base64 32` |
| `POSTGRES_PASSWORD` | Database password | none — Compose requires it | choose one |
| `SMTP_HOST` | SMTP server for password-reset emails | `mailhog` (Docker) / `smtp.gmail.com` (manual) | — |
| `SMTP_PORT` | SMTP port | `1025` (Docker) / `587` (manual) | — |
| `SMTP_USERNAME` | SMTP sender address (not needed with MailHog) | — | your email |
| `SMTP_PASSWORD` | SMTP app password (not needed with MailHog) | — | your app password |
| `OLLAMA_BASE_URL` | Ollama server URL | `http://localhost:11434` | — |
| `OLLAMA_MODEL` | LLM model name | `llama3.2` | — |
| `APP_BASE_URL` | Base URL used in password-reset email links | `http://localhost:8080` | — |
| `APP_FRONTEND_URL` | SPA origin the reset links point at | `http://localhost:5173` | — |
| `COOKIE_SECURE` | Set `true` in production (HTTPS only) | `false` | — |

> `JWT_SECRET` and `ENCRYPTION_KEY` have no defaults. Unset, the application fails to
> start: `JWT_SECRET` shorter than 32 bytes raises `WeakKeyException`, and an empty
> `ENCRYPTION_KEY` raises `IllegalArgumentException: Empty key`. Both are thrown during
> bean creation, so the stack trace names the bean rather than the variable.
>
> In production, always set `JWT_SECRET`, `ENCRYPTION_KEY`, and `POSTGRES_PASSWORD`
> via environment variables or a secrets manager. Never commit real secrets.

### Email in Docker mode

The Docker Compose stack includes a **MailHog** service, so password-reset and
other outgoing emails work out of the box with **no external mail account**.
`SMTP_HOST` defaults to `mailhog` and `SMTP_PORT` to `1025`; captured messages
are viewable in the MailHog web UI at **http://localhost:8025**.

To send real email instead (e.g. in manual dev mode without Docker), override
`SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, and `SMTP_PASSWORD` — for example with
a Gmail app password (`smtp.gmail.com`, port `587`).

## Running Locally (Dev Mode)

```bash
# 1. Start Postgres
cd dev-analytics && docker compose up -d db

# 2. Start Ollama (separate terminal)
ollama serve
# First time only — downloads ~2 GB:
ollama pull llama3.2

# 3. Start the backend  http://localhost:8080
cd dev-analytics
./mvnw spring-boot:run

# 4. Start the frontend dev server  http://localhost:5173
cd dev-analytics/frontend
npm install
npm run dev
```

The frontend dev server proxies `/api` requests to `localhost:8080`.

> In this manual dev mode Postgres runs in Docker but Ollama and mail do not.
> Configure `SMTP_*` for a real mail server if you need password-reset emails,
> or run the full Docker stack below to get MailHog automatically.

## One-Command Deployment (Docker Compose)

```bash
# 1. Copy and fill in secrets
cd dev-analytics
cp .env.example .env
# Edit .env — set JWT_SECRET, ENCRYPTION_KEY, POSTGRES_PASSWORD

# 2. Start the full stack
docker compose up -d
```

The app is available at **http://localhost:8080**.
Captured emails are viewable in MailHog at **http://localhost:8025**.
First boot pulls the llama3.2 model (~2 GB) — allow a few minutes before the AI features work.

## Building the Fat JAR

Maven does **not** build the frontend — there is no `frontend-maven-plugin`.
`src/main/resources/static/` is a committed build artifact, and `./mvnw package`
packages whatever that directory already holds. Any change under `frontend/src/`
needs `npm run build` first, and the rebuilt bundle belongs in the same commit.

```bash
cd dev-analytics/frontend
npm run build             # emits into ../src/main/resources/static/

cd ..
./mvnw clean package      # packages the static/ directory as it stands
java -jar target/dev-analytics-*.jar
```

## Running Tests

```bash
# Backend unit + integration tests
cd dev-analytics && ./mvnw test

# Frontend tests
cd dev-analytics/frontend && npm run test:run

# Lint
cd dev-analytics/frontend && npm run lint
```

## Demo Mode

Start with a pre-seeded dashboard — no real data source needed:

```bash
cd dev-analytics
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

**Login:** `demo@demo.com` / `demo`

On first boot the seeder creates 12 weeks of realistic metrics. Subsequent boots skip seeding (idempotent).
