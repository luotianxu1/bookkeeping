# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Monorepo for a mobile-first personal/family management app. Two independent components plus shared SQL:

- `vue3-ts-app/` — Vue 3 + TypeScript frontend (Vite, mobile-first)
- `spring-cloud-bookkeeping/` — Spring Boot 3.3.5 / Java 17 backend (Maven multi-module, one service per port)
- `database/` — MySQL schema files, numbered for sequential manual migration

See `AGENTS.md` for a condensed version of this guidance.

## Commands

### Frontend (`vue3-ts-app/`)
```bash
pnpm install          # pnpm 9.12.1 required
pnpm dev              # Vite dev server on 0.0.0.0:5173 (strict port)
pnpm build            # vue-tsc -b && vite build  — type-checks then builds
pnpm preview          # serve dist on 0.0.0.0:4173
```
No test framework and no linter are configured. `pnpm build` is the only verification gate (via `vue-tsc`).

### Backend (`spring-cloud-bookkeeping/`)
```bash
mvn clean install                              # build all modules
mvn clean install -pl common,auth-service      # single module (+ dep on common)
mvn test -pl tool-service                       # only tool-service has tests
```
Each service is a standalone Spring Boot app; run individually (e.g. `mvn spring-boot:run -pl finance-service`).

### Database
No migration framework — run files in numeric order:
```bash
mysql -u root -p bookkeeping_app < database/001_create_database_and_users.mysql.sql
mysql -u root -p bookkeeping_app < database/002_create_account_types.mysql.sql
# ...continue in order
```

## Backend architecture

Four services, all sharing one MySQL database `bookkeeping_app` (utf8mb4), plus a shared `common` module:

| Module | Port | Purpose |
|---|---|---|
| auth-service | 8081 | JWT auth, user/family management |
| finance-service | 8082 | Accounts, transactions, investments, gold, salary, debts |
| tool-service | 8083 | Contacts, todos, photography orders, calendar |
| food-service | 8084 | Food categories, dishes, orders |

Layering per service: `controller → service → mapper → entity`, DTOs in `dto/`.

- **Persistence is MyBatis-Plus** (not JPA). Mappers extend `BaseMapper<Entity>` and are typically empty — CRUD comes from the base class; write custom SQL only when needed.
- **Shared `common` module** provides the response envelope and security. All endpoints return `Result<T>` (`{ code, message, data, timestamp }`); `code == 0` means success (see `Result.ok()` / `ResultCodeEnum.SUCCESS`). `GlobalExceptionHandler` maps exceptions to this envelope.
- **Stateless JWT auth** (`common/security/`): `JwtTokenAuthenticationFilter` parses the `Bearer` token, sets a `UsernamePasswordAuthenticationToken` with the JWT subject (username) as principal and `ROLE_USER`. There is no DB session — every service validates tokens locally.
- **`app.jwt.secret` must be identical across all services** — it is how non-auth services validate tokens issued by auth-service.
- **Scheduled tasks** live in each service's `task/` package (e.g. finance `task/` has asset snapshots, fund/gold price sync, salary settlement). Cron expressions are configured in `application.yml` under service-specific keys (e.g. `finance.asset-snapshot.cron`), including a `market-closed-dates` list for trading-day logic.
- **API docs**: Knife4j at `/doc.html` on each service (Chinese UI).
- Lombok is used throughout for boilerplate.

## Frontend architecture

- **Path alias** `@` → `/src` (defined in `vite.config.ts`, not tsconfig).
- **Per-service API clients** in `src/api/request.ts`: `authRequest`, `financeRequest`, `toolRequest`, `foodRequest`, `adminRequest`. Each is created via `createRequest({ service })`. Use the `requestGet/requestPost/requestPut/requestDelete` helpers with a client, or the default `get/post/put/del` for the default service.
- **Response unwrapping**: `unwrapApiResponse` expects the backend `Result<T>` envelope and returns `data` directly; it throws `ApiError` when `code !== 0`. Callers receive `T`, not the envelope.
- **Auth flow**: JWT stored client-side (`utils/auth-token.ts`). A request interceptor attaches `Authorization: <tokenType> <accessToken>`. A `401` response clears the token + current user and triggers `showAuthPrompt` redirect to login. The router `beforeEach` guard does the same for unauthenticated navigation.
- **API base resolution** (`src/api/api-base-url.ts`): reads `VITE_*_API_BASE_URL` env vars, falling back to Vite dev-proxy paths (`/tool-api`, `/food-api`) or `http://localhost:808x`. In dev, `vite.config.ts` proxies `/auth-api`→8081, `/finance-api`→8082, `/tool-api`→8083, `/food-api`→8084.
- **Structure**: routes split per module in `src/router/modules/` (finance, food, tools, main); API modules in `src/api/modules/`; pages under `src/pages/{finance,food,tools,login,profile}`.
- **Mobile scaling**: `postcss-pxtorem` + `utils/rem.ts` for viewport scaling. Styling via SCSS. Charts via ECharts 6.

### Frontend env vars (`vue3-ts-app/.env`)
`VITE_API_BASE_URL` (default fallback), `VITE_AUTH_API_BASE_URL`, `VITE_FINANCE_API_BASE_URL`, `VITE_TOOL_API_BASE_URL`, `VITE_FOOD_API_BASE_URL`, `VITE_ADMIN_API_BASE_URL`.

## Gotchas

- **food-service uses package `com.example.tool`** (not `com.example.food`) — a copy-paste artifact. Don't rename without updating all references.
- **`app.jwt.secret` in each service's `application.yml`** ships with a placeholder value and must match across services; changing it in one place breaks cross-service auth.
- DB credentials are currently hardcoded in `application.yml` files.
