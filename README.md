# Expense Tracker Backend

A Personal Finance & Expense Analytics backend: register/login, add bank accounts,
upload bank/credit-card PDF statements for automatic parsing, categorize expenses via
a hybrid rules + local-LLM engine, manually track expenses/income, and view monthly/
yearly dashboard analytics. Financial calculations are always deterministic — AI is
used only to suggest a transaction's category, never to compute totals.

Full functional spec: [`src/main/resources/design.md`](src/main/resources/design.md).
Build history and phase-by-phase implementation notes: [`CLAUDE.md`](CLAUDE.md).

## Tech stack

- Java 21, Spring Boot 4.1.0 (Web, Data JPA, Security, Validation, AMQP, Actuator)
- PostgreSQL 16, Flyway migrations
- RabbitMQ (async statement processing)
- Apache PDFBox (PDF parsing, including password-protected statements)
- JWT auth (access + rotating refresh tokens)
- Ollama (optional, local LLM for expense categorization — no API key, no external calls)
- springdoc-openapi (Swagger UI)

## Running with Docker Compose

Brings up Postgres, RabbitMQ, and the backend together.

```bash
cp .env.example .env
# edit .env — at minimum set JWT_SECRET to your own random value
docker compose up --build
```

The API is then available at `http://localhost:8085`, Swagger UI at
`http://localhost:8085/swagger-ui/index.html`, and the RabbitMQ management UI at
`http://localhost:15672` (guest/guest by default).

AI categorization is **off by default** (`EXPENSE_AI_ENABLED=false`) since no Ollama
container is bundled — transactions that don't match a user rule or the built-in
merchant rule table simply fall back to the `OTHER` category rather than erroring.
To enable it:

1. Install and run [Ollama](https://ollama.com) on the host machine.
2. `ollama pull llama3.1` (or whichever model you set `OLLAMA_MODEL` to).
3. In `.env`, set `EXPENSE_AI_ENABLED=true`. `OLLAMA_BASE_URL` already defaults to
   `http://host.docker.internal:11434` so the container can reach an Ollama instance
   running on the host.

Uploaded statement files persist in the `statement-storage` named volume; Postgres
data persists in `postgres-data`. `docker compose down -v` removes both.

### Environment variables

See [`.env.example`](.env.example) for the full list with defaults. The only one you
should always change yourself is `JWT_SECRET` — the fallback baked into
`docker-compose.yml` is the same non-secret placeholder used for local dev in
`application.yml` and must not be relied on beyond a quick local trial.

## Running locally without Docker

Requires a local Postgres and RabbitMQ (see `application-local.yml` for the expected
connection defaults), then:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## API overview

All endpoints are under `/api/v1`. Full interactive documentation (request/response
schemas, try-it-out) is at `/swagger-ui/index.html` once running; raw OpenAPI JSON at
`/v3/api-docs`.

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` |
| Accounts | `POST/GET/PUT/DELETE /accounts`, `GET /accounts/{id}` |
| Categories | `GET /categories`, `GET /categories/{id}` |
| Statements | `POST /statements/upload` (multipart, optional `password`), `GET /statements`, `GET /statements/{id}` |
| Transactions | `POST /transactions/expenses`, `POST /transactions/income`, `GET /transactions` (filterable/paginated), `GET/PUT/DELETE /transactions/{id}`, `PATCH /transactions/{id}/category` |
| Categorization review | `GET /categorization/review`, `POST /categorization/{id}/approve`, `PATCH /categorization/{id}` |
| Dashboard | `GET /dashboard/monthly`, `GET /dashboard/yearly`, `GET /dashboard/trends` |

Authenticated endpoints require `Authorization: Bearer <accessToken>` from
`/auth/login` or `/auth/register`.

## Project status

Phases 1–10 and 12 (this) are complete; automated testing (originally planned as
Phase 11) was explicitly skipped per project decision — all functionality was
verified manually against real Postgres/RabbitMQ instances during development. See
`CLAUDE.md` for the full phase log, environment gotchas, and design decisions made
along the way.
