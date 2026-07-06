# Personal Developer Analytics

Master's thesis project — a self-hosted platform that aggregates developer productivity
metrics from local Git repositories, GitHub, and Jira, and surfaces them through
a React dashboard with AI-generated insights via local Ollama.

## Architecture

| Layer | Technology |
|-------|-----------|
| Backend API | Spring Boot 3.5, Java 17, JWT auth |
| Database | PostgreSQL 16, Flyway migrations |
| Frontend SPA | React 18, TypeScript, Vite, Tailwind CSS 3, Recharts |
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
| `JWT_SECRET` | JWT signing key | dev key (**insecure**) | `openssl rand -hex 32` |
| `ENCRYPTION_KEY` | AES-256-GCM token encryption key (base64) | dev key (**insecure**) | `openssl rand -base64 32` |
| `POSTGRES_PASSWORD` | Database password | `123` (dev only) | choose one |
| `SMTP_HOST` | SMTP server for password-reset emails | `mailhog` (Docker) / `smtp.gmail.com` (manual) | — |
| `SMTP_PORT` | SMTP port | `1025` (Docker) / `587` (manual) | — |
| `SMTP_USERNAME` | SMTP sender address (not needed with MailHog) | — | your email |
| `SMTP_PASSWORD` | SMTP app password (not needed with MailHog) | — | your app password |
| `OLLAMA_BASE_URL` | Ollama server URL | `http://localhost:11434` | — |
| `COOKIE_SECURE` | Set `true` in production (HTTPS only) | `false` | — |

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

```bash
cd dev-analytics
./mvnw clean package      # builds frontend, embeds it in the JAR
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
./mvnw spring-boot:run --spring.profiles.active=demo
```

**Login:** `demo@demo.com` / `demo`

On first boot the seeder creates 12 weeks of realistic metrics. Subsequent boots skip seeding (idempotent).