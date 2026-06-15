# AGENTS.md

## Project Structure

Monorepo with two independent components:
- `vue3-ts-app/` — Vue 3 mobile-first frontend
- `spring-cloud-bookkeeping/` — Spring Cloud Java backend (Maven multi-module)
- `database/` — MySQL schema files (numbered for sequential migration)

## Frontend (Vue 3 + TypeScript)

```bash
cd vue3-ts-app
pnpm install          # pnpm 9.12.1 required
pnpm dev              # Vite dev server on 0.0.0.0
pnpm build            # vue-tsc -b && vite build
```

- Path alias: `@` → `/src`
- API proxy routes: `/auth-api` → 8081, `/finance-api` → 8082, `/tool-api` → 8083, `/food-api` → 8084
- Uses `postcss-pxtorem` for mobile viewport scaling
- API client: `src/api/request.ts` — exports per-service instances (`authRequest`, `financeRequest`, etc.)
- Auth: JWT stored client-side, 401 triggers redirect to login

## Backend (Spring Boot 3.3.5 + Java 17)

```bash
cd spring-cloud-bookkeeping
mvn clean install     # builds all modules
mvn clean install -pl common,auth-service   # single module
```

| Module | Port | Purpose |
|---|---|---|
| auth-service | 8081 | JWT auth, user/family management |
| finance-service | 8082 | Accounts, transactions, investments |
| tool-service | 8083 | Contacts, todos, photography orders, calendar |
| food-service | 8084 | Food categories, dishes, orders |

- All services share one MySQL database: `bookkeeping_app`
- Database: MySQL 8.0+, charset utf8mb4
- Schema migrations: run `database/` files in numeric order (001, 002, ...)
- JWT secret must match across all services (`app.jwt.secret` in application.yml)
- Knife4j enabled for API docs at `/doc.html` on each service
- Uses Lombok for boilerplate reduction

## Gotcha: Food Service Package Name

`food-service` Java code uses package `com.example.tool` (not `com.example.food`). This appears to be a copy-paste artifact. Don't rename without updating all references.

## Database

No migration framework — manual SQL execution. Files are numbered:
```bash
mysql -u root -p bookkeeping_app < database/001_create_database_and_users.mysql.sql
mysql -u root -p bookkeeping_app < database/002_create_account_types.mysql.sql
# ... run in order
```

## Testing

- Backend: minimal (only `tool-service/src/test/` exists). Run: `mvn test -pl tool-service`
- Frontend: no test framework configured

## Environment Variables (Frontend)

In `vue3-ts-app/.env` or shell:
- `VITE_API_BASE_URL` — default API base (fallback: http://localhost:8081)
- `VITE_AUTH_API_BASE_URL`, `VITE_FINANCE_API_BASE_URL`, `VITE_TOOL_API_BASE_URL`, `VITE_FOOD_API_BASE_URL`
