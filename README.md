# Expense Approval

A full-stack **expense reimbursement approval workflow**: employees file expense requests, managers
approve them for their department, and finance gives final sign-off. Built to show clean **REST API
design, relational schema design, and JWT authentication with role-based access control** — no
distributed-systems machinery, just a focused, defensible product.

**Stack:** Java 21 · Spring Boot 4.1 · PostgreSQL + Flyway · Spring Security + JWT (jjwt) · React 19 + Vite + TypeScript.

**Live demo:** <https://lijuantang-expense.duckdns.org> — every seeded account uses the password
`password123` (full list [below](#demo-accounts)). The quickest tour of the authorization model:
file a request as `alice@acme.com` (Engineering employee), approve it as `bob@acme.com` (Engineering
manager), then sign in as `dave@acme.com` (Sales manager) and confirm Alice's request isn't his to
act on — department scoping is enforced server-side, not hidden in the UI. `fiona@acme.com` (finance)
gives the final sign-off. Running on an Oracle always-free VM behind Caddy (automatic HTTPS).

---

## What it demonstrates

- **11 REST endpoints** across auth, expense requests, and departments.
- **5-table relational schema** owned by versioned Flyway migrations.
- **Stateless JWT auth** — a per-request filter rebuilds the principal from the token's claims, so
  authenticated requests never touch the database for identity.
- **Three roles** (`EMPLOYEE` / `MANAGER` / `FINANCE`) enforced with method-level `@PreAuthorize`
  **and** fine-grained checks in the service layer (defense in depth).
- A **state machine** with an append-only **audit trail** of every transition.

## Roles & the authorization rules

| Role | Can create | Can see | Can decide |
|---|---|---|---|
| `EMPLOYEE` | own requests | own requests | — |
| `MANAGER` | own requests | their **department** | approve/reject **SUBMITTED** requests in their department |
| `FINANCE` | own requests | **all** requests | approve/reject **MANAGER_APPROVED** requests org-wide |

Three guarantees are unit-tested:

1. **The role owns the stage** — only a manager can act on `SUBMITTED`, only finance on `MANAGER_APPROVED`.
2. **A manager is department-scoped** — they cannot touch another department's requests.
3. **Nobody can decide on their own request** — even a manager approving a request they filed is rejected.

## State machine

```
DRAFT ──submit──▶ SUBMITTED ──approve──▶ MANAGER_APPROVED ──approve──▶ FINANCE_APPROVED
                      │                        │
                      └────────reject──────────┴──▶ REJECTED
```

Every transition writes an immutable row to `approvals` (who, action, from → to, comment, when).

## Data model

`departments` · `users` (one role each, FK department) · `expense_requests` (FK requester + department,
status, denormalised total) · `expense_items` (FK request, cascade-deleted) · `approvals` (append-only
audit trail). See [`V1__init_schema.sql`](src/main/resources/db/migration/V1__init_schema.sql) — Flyway
is the single source of truth for the schema; Hibernate never mutates it.

## API

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | public | create an account |
| POST | `/api/auth/login` | public | obtain a JWT |
| GET | `/api/auth/me` | authenticated | current user |
| GET | `/api/departments` | authenticated | list departments |
| POST | `/api/requests` | authenticated | create a draft |
| GET | `/api/requests` | authenticated | list (role-scoped, `?status=` filter, paged) |
| GET | `/api/requests/{id}` | authenticated | detail + line items + approval trail |
| PUT | `/api/requests/{id}` | owner | edit a draft |
| POST | `/api/requests/{id}/submit` | owner | DRAFT → SUBMITTED |
| POST | `/api/requests/{id}/approve` | manager/finance | advance one stage |
| POST | `/api/requests/{id}/reject` | manager/finance | → REJECTED |

## Running it

```bash
# 1) Postgres (host port 5433)
docker compose up -d db

# 2) Backend (http://localhost:8081) — needs Java 21
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw spring-boot:run

# 3) Frontend (http://localhost:5173)
cd frontend && npm install && npm run dev
```

### Demo accounts

On first boot the app seeds three departments and five demo accounts (password `password123`):

| Email | Role | Department |
|---|---|---|
| alice@acme.com | EMPLOYEE | Engineering |
| bob@acme.com | MANAGER | Engineering |
| carol@acme.com | EMPLOYEE | Sales |
| dave@acme.com | MANAGER | Sales |
| fiona@acme.com | FINANCE | Finance |

## Tests

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw test
```

15 tests, no database required: the state-machine transition table plus the RBAC guarantees
(role-owns-stage, department scoping, no self-approval) with mocked repositories.

These are unit tests with mocked repositories, so they deliberately do not cover wiring: a broken
Flyway migration, a bad `@Query`, or a malformed `@PreAuthorize` expression would still let the suite
pass. A `@SpringBootTest` context-load test against Testcontainers is the obvious next addition.

## Deployment

The app ships as a **single container**: a multi-stage `Dockerfile` builds the React SPA, embeds it
in the Spring Boot jar (served from `classpath:/static`), and runs it on a slim JRE. Spring serves
both the API (`/api/**`) and the SPA (with a forwarding controller so client-side deep links work),
so there's **one image, one URL, no CORS** in production.

**Whole stack locally (app + Postgres) in one command:**

```bash
docker compose up --build        # app on http://localhost:8081
```

**Fly.io (live demo):**

```bash
fly launch --no-deploy           # pick a unique app name; keeps the provided fly.toml
fly postgres create              # managed Postgres; then attach it:
fly postgres attach <pg-app-name>
# Point the app at the DB (JDBC form) and set a real JWT secret:
fly secrets set \
  DB_URL="jdbc:postgresql://<pg-host>.flycast:5432/<db>" \
  DB_USER="<user>" DB_PASSWORD="<password>" \
  APP_JWT_SECRET="$(openssl rand -base64 48)"
fly deploy
```

**AWS alternative:** the same image runs on **AWS App Runner** (point it at the container, set the
`DB_URL` / `DB_USER` / `DB_PASSWORD` / `APP_JWT_SECRET` env vars) with **RDS for PostgreSQL** — no
code changes, just a different target.

## Design notes

- **Stateless JWT, no server session.** The token carries `sub` (user id), `role`, and `dept`; the
  filter builds the principal from those claims, so authorization needs no per-request DB lookup. The
  trade-off is that a role/department change only takes effect on the next login — acceptable here,
  and the seam to re-check the DB is a one-line change.
- **Flyway owns the schema (`ddl-auto=validate`).** Hand-written, reviewable SQL migrations are the
  source of truth; entities map to them, and Hibernate is allowed to verify that mapping at startup
  but never to mutate it. (`validate` initially failed against Postgres over `timestamptz`/`text`/`char`
  mismatches; migration `V2` fixed the column types rather than turning the check off, so a drift
  between an entity and the schema now fails the boot instead of surfacing at runtime.)
- **Optimistic locking on approvals (`@Version` → `409`).** Two approvers acting on the same request
  race between reading its state and writing the transition. A version column makes the second commit
  fail, and `GlobalExceptionHandler` maps that to `409 Conflict` — the request was valid, it just lost
  the race, which is a different failure from "bad input" or "not allowed".
- **Denormalised `total_amount`** on the request keeps list/filter queries cheap; it is recomputed
  from the line items on every write.
- **404 instead of 403 on cross-department reads** so the API doesn't leak the existence of other
  departments' requests.
