# AGENTS.md

## Project Structure

Monorepo with two independent components:

- `web/` — Vue 3 mobile-first frontend
- `life-hub/` — Spring Boot Java backend (Maven multi-module: common + auth-service + app-service)
- `database/` — MySQL schema files (numbered for sequential migration)

## Frontend (Vue 3 + TypeScript)

```bash
cd web
pnpm install          # pnpm 9.12.1 required
pnpm dev              # Vite dev server on 0.0.0.0
pnpm build            # vue-tsc -b && vite build
```

- Path alias: `@` → `/src`
- API proxy routes: `/auth-api` → 8081, `/finance-api` / `/tool-api` / `/food-api` → 8082（前缀保留仅为兼容，三个都打业务服务）, `/market-api` → 8083
- Uses `postcss-pxtorem` for mobile viewport scaling
- API client: `src/api/request.ts` — exports per-service instances (`authRequest`, `financeRequest`, etc.)
- Auth: JWT in httpOnly cookie (`access_token`, `SameSite=Lax`), `withCredentials: true` in request.ts; 401 clears credentials and redirects to login. Token is NOT stored in localStorage.

## Backend (Spring Boot 3.3.5 + Java 17)

```bash
cd life-hub
mvn clean install     # builds all modules
mvn clean install -pl common,auth-service   # single module
```

| Module | Port | Purpose |
| --- | --- | --- |
| auth-service | 8081 | JWT auth, user/family management |
| app-service | 8082 | 全部业务：finance（账户/交易/投资/选股）+ tools（contacts/todos/photography/calendar，`/api/tools/*`）+ food（`/api/tools/food/*`） |
| market-service | 8083 | 行情中心：`/api/market/*` 全球行情/K线/涨跌停/市场状态/要闻/美股盘前；JWT 认证与 app-service 同套（同 JWT_SECRET、同一 MySQL 用户状态校验），无业务表 |

- 2026-10: 项目更名 life-hub（原 spring-cloud-bookkeeping），前端目录更名 web/（原 vue3-ts-app）。tool-service / food-service 已合并进业务服务并整体更名 app-service（原 finance-service；包名 `com.example.app`/主类 `AppServiceApplication`）。历史备份在 `life-hub/data/backup-20261029/`。跨服务 Client（`FinanceClient` / `ToolServiceClient`）已改为同 JVM 本地调用；internal 接口与 X-Internal-Key 鉴权保留为兼容入口。数据库与 tunnel 名保留 `bookkeeping` 不变。
- 2026-10 下旬: 行情代码（原 `com.example.app.market` 包 + 行情 controllers/services/DTOs）整体迁出为 `market-service`（包 `com.example.market`，路由 `/api/finance/market*` → `/api/market/*`）。app-service 选股引擎经 `/api/internal/market/kline`（`MarketClient`，X-Internal-Key）调行情中心取日K；前端 market 接口走 `/market-api` 代理 + `marketRequest`。market-service 认证与 app-service 一致：scanBasePackages "com.example" 复用 common 全套 security（JWT Cookie + DefaultSecurityConfig + UserStatusChecker 查库），需 JWT_SECRET/DB_PASSWORD；曾做过的匿名开放+可选 MARKET_API_KEY 方案已废弃（ApiKeyFilter 已删）。
- All services share one MySQL database: `bookkeeping_app`
- Database: MySQL 8.0+, charset utf8mb4
- Schema migrations: run `database/` files in numeric order (001, 002, ...)
- JWT secret must match across all services (`app.jwt.secret` in application.yml)
- Knife4j enabled for API docs at `/doc.html` on each service
- Uses Lombok for boilerplate reduction

## Database

No migration framework — manual SQL execution. Files are numbered:

```bash
mysql -u root -p bookkeeping_app < database/001_create_database_and_users.mysql.sql
mysql -u root -p bookkeeping_app < database/002_create_account_types.mysql.sql
# ... run in order
```

## Testing

- Backend: no tests yet（原 tool-service 的过时测试已随合并删除）
- Frontend: no test framework configured

## Environment Variables (Frontend)

In `web/.env` or shell:

- `VITE_API_BASE_URL` — default API base (fallback: <http://localhost:8081>)
- `VITE_AUTH_API_BASE_URL`, `VITE_FINANCE_API_BASE_URL`, `VITE_TOOL_API_BASE_URL`, `VITE_FOOD_API_BASE_URL`

## Cloudflare Tunnel (External Access)

Named tunnel `bookkeeping` is configured: <https://luotianxu.dpdns.org> → <http://localhost:4173> (Vite preview). Config at `~/.cloudflared/config.yml` (protocol http2). Start with `cloudflared tunnel run bookkeeping`. See README "外网访问（Cloudflare Tunnel）" for details. `vite.config.ts` `preview.allowedHosts` must include `luotianxu.dpdns.org`.
