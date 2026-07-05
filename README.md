# InsuranceCRM — Backend

Spring Boot 4 / MongoDB backend for an insurance agency CRM: customers, leads, communication logging, agent performance, and renewal reminders. Role-based access for `ADMIN` and `AGENT` users, secured with JWT.

## Prerequisites

- Java 21
- MongoDB running locally (or reachable via `MONGODB_URI`) — default: `mongodb://localhost:27017/test`

## Required environment variables

| Variable | Required | Default | Notes |
|---|---|---|---|
| `JWT_SECRET` | **Yes** | — | HMAC signing key for JWTs, 32+ characters. The app **fails to start** without this — there is deliberately no baked-in default, since this repo is public. Generate one yourself, e.g. `openssl rand -base64 48`. |
| `MONGODB_URI` | No | `mongodb://localhost:27017/test` | Full Mongo connection string, including the database name. |

Set them however suits your setup — a shell export, an IDE run configuration, or a `.env`-style tool of your choice (not read automatically by this app).

## Running locally

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
./gradlew bootRun
```

The app starts on **http://localhost:8081**. On first run against an empty database, a default admin user is seeded automatically (see `DataInitializer`) — check the startup logs for its credentials.

API docs (Swagger UI): `http://localhost:8081/swagger-ui.html`

## Running tests

```bash
./gradlew test
```

Unit tests use Mockito (no external dependencies). Integration tests under `src/test/java/.../controller` need a local MongoDB instance and use a separate database (`insurancecrm_test`, configured in `src/test/resources/application.yaml`) so they never touch your development data.

## Tech stack

Spring Boot 4, Spring Security (JWT), Spring Data MongoDB, Apache POI (Excel import/export), Apache Commons CSV, springdoc-openapi.
