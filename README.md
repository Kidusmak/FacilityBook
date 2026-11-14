# FacilityBook — Enterprise Facility & Resource Reservation System

FacilityBook lets employees discover shared resources (meeting rooms, conference halls,
training rooms, projectors, company vehicles), reserve them without double-booking, and
track their bookings, while authorized staff manage resources, availability, approvals,
maintenance, and reporting.

This repository contains the complete project deliverables:

| Part | Technology | Location |
| --- | --- | --- |
| Backend API | Java 21, Spring Boot 4, Spring Web, Spring Data JPA, Spring Security (JWT) | `src/` |
| Frontend SPA | React 19 + TypeScript, React Router, Redux Toolkit, RTK Query, React Hook Form, Zod | `frontend/` |
| Database | PostgreSQL 16, schema managed by Flyway | `src/main/resources/db/migration` |
| API docs | springdoc-openapi (Swagger UI) | `/swagger-ui.html` |
| Infrastructure | Docker, Docker Compose, Maven, Node.js | `Dockerfile`, `docker-compose.yml` |
| Documentation | SRS, ERD, use cases, architecture, user guide, test report | `docs/` |

---

## 1. Prerequisites

| Tool | Version used | Notes |
| --- | --- | --- |
| Docker Desktop | 29.x (Engine 1.5x API) | Runs both the database and the API via Compose. |
| Docker Compose | v2 (`docker compose`) | Bundled with Docker Desktop. |
| Java (JDK) | 21 | Only needed to build/test the backend outside Docker. |
| Maven | 3.9+ | Or use the bundled `./mvnw` / `mvnw.cmd` wrapper. |
| Node.js | 20+ | Only needed to run the frontend. |
| npm | 10+ | Ships with Node.js. |

> The whole application (database + backend) starts with a single `docker compose up`.
> Java, Maven, and Node are only required if you want to build or test the projects
> directly on your machine.

---

## 2. Environment variables

Credentials and secrets are supplied through environment variables. Nothing secret is
committed to source control — only a safe `.env.example` is tracked.

### 2.1 Docker Compose

Copy the example file and adjust as needed (the file documents every variable):

```bash
cp .env.example .env
```

| Variable | Default | Purpose |
| --- | --- | --- |
| `POSTGRES_DB` | `facilitybook` | Database name created in the Postgres container. |
| `POSTGRES_USER` | `facilitybook` | Database user. |
| `POSTGRES_PASSWORD` | `facilitybook` | Database password. |
| `POSTGRES_HOST_PORT` | `5433` | Host port mapped to the container's `5432`. Defaults to 5433 to avoid clashing with a local Postgres. |
| `APP_HOST_PORT` | `8080` | Host port mapped to the API's `8080`. |
| `APP_TIMEZONE` | `Europe/Istanbul` | Timezone used to interpret operating hours and group report data. |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Browser origins allowed by CORS. |
| `APP_JWT_SECRET` | dev value | HS256 signing key. **Change for anything but a local demo.** |
| `APP_JWT_EXPIRATION_MINUTES` | `60` | JWT lifetime in minutes. |
| `APP_REMINDERS_ENABLED` | `true` | Enables the scheduled in-app reservation reminder job. |
| `APP_REMINDERS_INTERVAL_MS` | `3600000` | Reminder job interval. |
| `APP_REMINDERS_HORIZON_HOURS` | `24` | How far ahead the reminder job looks. |

The backend reads the same names directly (see
`src/main/resources/application.properties`), so it also works without Docker if you
export them yourself.

### 2.2 Frontend

```bash
cd frontend
cp .env.example .env
```

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_API_URL` | `http://localhost:8080/api` | Base URL of the Spring Boot API. |

---

## 3. Setup and run

### 3.1 Option A — everything in Docker (recommended)

From the repository root:

```bash
# Build the API image and start Postgres + API
docker compose up --build -d

# Follow the API logs
docker logs -f facilitybook-app

# Stop everything (keeps the database volume)
docker compose down

# Stop everything AND delete the database (fresh demo next start)
docker compose down -v
```

The API is ready when `docker compose ps` shows the app `Up` and
`curl http://localhost:8080/actuator/health` returns `{"status":"UP"}`.
On first start, Flyway creates the schema and seeds demo data automatically.

### 3.2 Option B — backend on the host, database in Docker

```bash
# 1. Start only the database
docker compose up -d db

# 2. Run the API against it (defaults already point at localhost:5433)
./mvnw spring-boot:run
```

### 3.3 Frontend (development)

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

Other frontend commands:

```bash
npm run build      # type-check + production build
npm test           # run the Vitest suite
```

---

## 4. Demo accounts

All demo users share the password **`Password123!`**. They are created by the Flyway
seed migration `V12__seed_demo_departments_and_users.sql`.

| Role | Email | Password |
| --- | --- | --- |
| ADMIN | `admin@facilitybook.local` | `Password123!` |
| EMPLOYEE | `employee@facilitybook.local` | `Password123!` |
| FACILITY_MANAGER | `facility@facilitybook.local` | `Password123!` |
| DEPARTMENT_MANAGER | `deptmanager@facilitybook.local` | `Password123!` |
| RESOURCE_OPERATOR | `operator@facilitybook.local` | `Password123!` |

The seed migration `V13__seed_demo_facilities.sql` also creates demo buildings,
six resources (rooms, a hall, a projector, a van), and their operating hours. No
reservations are seeded so every demo starts with an empty booking calendar.

---

## 5. API URL and documentation

| Resource | URL |
| --- | --- |
| API base URL | `http://localhost:8080/api` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Health check | `http://localhost:8080/actuator/health` |
| Frontend (dev) | `http://localhost:5173` |

Authenticate at `POST /api/auth/login` with an email and password; the response
contains an `accessToken`. Send it as `Authorization: Bearer <accessToken>` on every
protected request.

---

## 6. Tests

```bash
# Backend — JUnit 5 + Mockito, integration tests run on Testcontainers Postgres
./mvnw clean test          # or: mvn clean test

# Frontend — Vitest + React Testing Library
cd frontend && npm test
```

The backend suite has **33 tests** (unit + Testcontainers integration) and the
frontend suite has **17 tests**. See [`docs/TEST_REPORT.md`](docs/TEST_REPORT.md) for
the full breakdown.

---

## 7. Screenshots

Screenshots of the main workflows live in [`docs/screenshots/`](docs/screenshots/) and
are referenced from the user guide. Add them with the file names listed there.

---

## 8. Project structure

```
Facilitybook/
├── docker-compose.yml           # Postgres + API stack
├── Dockerfile                   # Multi-stage API image
├── .env.example                 # Compose/backend environment template
├── pom.xml                      # Maven build
├── src/main/java/...            # Backend (controller → service → repository → entity)
│   ├── config/                  # Security, OpenAPI, CORS, scheduling
│   ├── web/                     # REST controllers (DTO in/out)
│   ├── service/                 # Business rules
│   ├── repository/              # Spring Data JPA repositories
│   ├── entity/                  # JPA entities + enums
│   ├── dto/                     # Request/response records
│   ├── security/                # JWT filter, principal, handlers
│   └── exception/               # Consistent ApiError handling
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/            # Flyway V1..V13
├── src/test/java/...            # Unit + integration tests
├── frontend/                    # React + TypeScript SPA
└── docs/                        # SRS, ERD, use cases, architecture, guides, report
```

---

## 9. Known limitations

- **In-app notifications only.** Email and WebSocket delivery are out of scope
  (optional enhancements in the brief). A scheduled job creates "upcoming reservation"
  reminders; there is no external push.
- **Single facility timezone.** Operating hours are stored as local wall-clock times and
  interpreted in one configured timezone (`APP_TIMEZONE`). Multi-timezone facilities are
  not supported.
- **Operating hours are simple daily windows.** One open/close window per resource per
  weekday; no split shifts (e.g. closed over lunch) or per-date exceptions.
- **One approval decision per reservation.** The workflow is approve/reject with a
  reason; multi-step or multi-approver chains are not modelled.
- **No recurring reservations.** Each booking is created individually.
- **Demo JWT secret.** The default `APP_JWT_SECRET` is a development value and must be
  replaced for any non-demo deployment.
- **Port 5433 default.** The compose database is exposed on host port `5433` by default
  to avoid clashing with a local PostgreSQL; change `POSTGRES_HOST_PORT` if needed.
- **No file attachments.** Damage reports are text-only.

---

## 10. Documentation index

| Document | Contents |
| --- | --- |
| [`docs/SRS.md`](docs/SRS.md) | Software Requirements Specification: scope, roles, functional and non-functional requirements. |
| [`docs/ERD.md`](docs/ERD.md) | Entity-relationship diagram and table descriptions. |
| [`docs/USE_CASES.md`](docs/USE_CASES.md) | Use-case diagram and detailed use-case descriptions. |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Architecture diagram and layer/security design decisions. |
| [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md) | Step-by-step guide for each role and workflow. |
| [`docs/TEST_REPORT.md`](docs/TEST_REPORT.md) | Automated test inventory, coverage of business rules, and how to run them. |
| [`docs/CONTRIBUTION_REPORT.md`](docs/CONTRIBUTION_REPORT.md) | Template for each intern's individual contribution report. |
