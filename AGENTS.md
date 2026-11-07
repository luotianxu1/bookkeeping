# AGENTS.md

## Project Structure

Monorepo with two independent components:

- `web/` — Vue 3 mobile-first frontend
- `life-hub/` — Spring Boot Java backend (Maven multi-module: common + auth-service + app-service + market-service)
- `database/` — MySQL schema files (numbered for fresh database initialization)

## Frontend (Vue 3 + TypeScript)

```bash
cd web
pnpm install          # pnpm 9.12.1 required
pnpm dev              # Vite dev server on 0.0.0.0
pnpm test             # Vitest + Vue Test Utils
pnpm test:e2e         # Node 22; builds production assets + Playwright Chromium (install browser first)
pnpm test:e2e:live    # Java 17 + Maven; real auth/finance HTTP servers + isolated H2 + Chromium
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
| market-service | 8083 | 行情中心：`/api/market/*` 全球行情/K线/涨跌停/市场状态/要闻/美股盘前/金价（金投网源，含品牌首饰金价与走势）；JWT 认证与 app-service 同套（同 JWT_SECRET，用户状态/家庭范围经 auth-service 内部接口校验），无数据库依赖 |

- 2026-10: 项目更名 life-hub（原 spring-cloud-bookkeeping），前端目录更名 web/（原 vue3-ts-app）。tool-service / food-service 已合并进业务服务并整体更名 app-service（原 finance-service；包名 `com.example.app`/主类 `AppServiceApplication`）。旧备份目录已于 2026-11-04 清理，历史实现从 Git 查阅。跨服务 Client（`FinanceClient` / `ToolServiceClient`）已改为同 JVM 本地调用；internal 接口与 X-Internal-Key 鉴权保留为兼容入口。数据库与 tunnel 名保留 `bookkeeping` 不变。
- 2026-10 下旬: 行情代码（原 `com.example.app.market` 包 + 行情 controllers/services/DTOs）整体迁出为 `market-service`（包 `com.example.market`，路由 `/api/finance/market*` → `/api/market/*`）。app-service 选股引擎经 `/api/internal/market/kline`（`MarketClient`，X-Internal-Key）调行情中心取日K；前端 market 接口走 `/market-api` 代理 + `marketRequest`。market-service 认证与 app-service 一致：scanBasePackages "com.example" 复用 common 全套 security（JWT Cookie + DefaultSecurityConfig）；2026-11-04 起改为远程 UserStatusChecker/FamilyScopeChecker 经 auth-service 校验，需 JWT_SECRET/INTERNAL_API_KEY，不再需要 DB_PASSWORD；曾做过的匿名开放+可选 MARKET_API_KEY 方案已废弃（ApiKeyFilter 已删）。
- 2026-10-30: 金价（金投网 jijinhao 源：黄金9999/伦敦金/品牌首饰金价/走势，数据口径不变）自 app-service 整体迁入 market-service（包 `com.example.market.gold`，对外 `/api/market/gold-prices`，内部 `/api/internal/gold/**`）。app-service `GoldPriceService` 变为薄适配层（`MarketGoldClient`，X-Internal-Key），方法签名不变，金账户/资产趋势/投资估值等内部消费方与 `GoldPriceCacheTask` 零改动；app-service 旧 `GoldPriceController` 已删除。前端 `getGoldPrices`/`getRealtimeGoldPrice` 改走 `marketRequest` + `/api/market/gold-prices`，响应结构不变。
- auth-service/app-service share MySQL `bookkeeping_app`; market-service has no JDBC/MySQL dependency and uses `market.auth-base-url` (default localhost:8081) for identity checks.
- Database: MySQL 8.0+, charset utf8mb4
- Fresh database initialization: run `database/` files in numeric order (001, 002, ...). Each CREATE TABLE includes the final schema; standalone migration/data-sync scripts have been consolidated and removed.
- JWT secret must match across all services (`app.jwt.secret` in application.yml)
- Knife4j enabled for API docs at `/doc.html` on each service
- Uses Lombok for boilerplate reduction

## Database

No migration framework — SQL files define the complete current schema for a fresh database. CREATE TABLE IF NOT EXISTS does not upgrade existing tables. Only missing admin/account-type/system-category defaults are inserted; no demo business data or historical backfill. Files are numbered:

```bash
mysql -u root -p bookkeeping_app < database/001_create_database_and_users.mysql.sql
mysql -u root -p bookkeeping_app < database/002_create_account_types.mysql.sql
# ... run in order
```

## Testing

- Backend: JUnit 5 + Mockito + isolated H2 databases. `cd life-hub && mvn -B -ntp clean verify` runs tests and builds all modules; focused: `mvn test -pl common,app-service -am`. No development DB or upstream credentials needed. H2 does not replace MySQL integration testing.
- Frontend: Vitest + Vue Test Utils + jsdom. `cd web && pnpm test`; production check: `pnpm build`.
- CI: `.github/workflows/ci.yml`, Java 17 / Node 22 / pnpm 9.12.1, push and pull request checks.
- MySQL integration: set `MYSQL_TEST_URL` (server URL with trailing slash), `MYSQL_TEST_USER`, `MYSQL_TEST_PASSWORD` to an isolated MySQL 8 instance. Tests create/drop only random `life_hub_test_*` schemas and use production table DDL. CI supplies MySQL 8.4; without the URL the integration test is skipped.
- Browser regression: `cd web && pnpm exec playwright install chromium && pnpm test:e2e`; controlled API responses, production build at 127.0.0.1:4175. Failure artifacts in playwright-report/ and test-results/.
- Live browser integration: `cd web && pnpm test:e2e:live` builds the optional Maven `live-e2e` profile's live-tests fixture module. Auth/finance HTTP servers bind 127.0.0.1:18881/18882 with a fresh H2 database; frontend preview binds 4176. Use production controllers/services/mappers/security, never mock the auth/account/transaction APIs or connect a development database. No scheduled jobs or external quote requests; the local unavailable gold endpoint exercises the existing login fallback. Keep the fixture module out of deployments. Failure artifacts in playwright-live-report/ and test-results/.
- Cash/investment POSTs, including fund/stock/gold position creation and gold sales, accept optional requestId. Keep replay lookup inside the user lock, persist response in the same transaction; investment may also check committed requests before remote quotes. Form retries retain the ID and automatically generated tradeAt only during the current page lifetime; generate fresh values after success or payload changes.
- Investment product identity is product_type + normalized symbol/market (trim, uppercase; NULL/blank market is empty). Use insertIfAbsent plus a current read inside the position transaction; never create products before remote quotes or overwrite existing product metadata on a duplicate. Final product identity columns and unique index are in database/007; request records are in database/043.
- MySqlAccountingTest#freshSchemaIncludesAllFeaturesAndPreservesInitialDefaults executes all initialization scripts in a random isolated schema, skips CREATE DATABASE/USE, checks default seeds and recovery constraints. Product identity is checked by productIdentityIsEnforcedByFreshTableDefinition. These focused checks also run on MySQL 5.7; full accounting still requires MySQL 8.
- UserStatusCache must never extend trusted status on failed refresh. Auth internal user-status uses isActiveFresh to avoid stacked caches; unavailable checks return 503, preserving the browser session.
- RemoteFamilyScopeChecker trusts verified membership for at most 60 seconds. Store the successful verification deadline separately from failure-cache TTL; after expiry, failed refreshes must return only selfId. Failure backoff must never renew old membership, and successful refresh must replace it with the latest immutable set.
- Dynamically import utils/echarts for line charts, echarts-statistics for bar/pie, echarts-investment for trade markers/zoom, echarts-kline for candlesticks. Do not force all ECharts modules into one manual chunk.
- 分类预算、退款/报销完整结构分别在 database/005、006、045 中定义；005 / 006 包含 MySQL 5.7 金额校验触发器，MySQL 8 使用 CHECK。关联到账在 transactions 中保留 type=expense、amount<0、original_transaction_id 和 recovery_kind，按到账日冲减原分类支出；不能作为普通收入或再次从原支出扣除。撤销须以正数原子扣回现金并校验余额；原支出有到账记录或报销计划时不能编辑/作废。报销计划本身不影响余额。分类预算按具体分类、月份和 CNY 现金净支出统计；提醒仅当前月，80%/100% 分阶段保存状态。
- Financial mutations must acquire `AccountMapper.lockUserForWrite(userId)` inside the transaction before reading mutable accounting state. Keep external quote/fee HTTP outside the transaction; re-read positions, pending trades and scheduled plans after locking. Cash balance changes use atomic mapper SQL.
- 储蓄目标和存取记录表在 database/048：一个现金账户可关联多个目标，各目标已存金额为自身存入减取出，从 0 开始。目标记录本身不改现金余额；收支和转账可选传 savingsAllocations，与现金变更同事务保存，修改或撤销按最终净变化校验并同步目标记录。所有现金扣款和手动调账后的余额不得低于账户全部目标已存总额，AccountMapper 原子扣款统一保护预留。手动存入占用可分配余额，取出释放预留且不得超过本目标已存金额。全部写入使用用户账务锁，新建及存取支持 requestId。目标关联流水时须先取消流水分配才能换账户；有存取记录时不能换币种；删除目标须同事务删除其记录并释放预留。账户仍有关联目标时禁止删除、停用及更改类型/币种。记录日期、截止日期和每月建议使用北京时间，建议按含首尾的自然月均摊剩余金额。
- Reimbursement follow-ups are created in database/005 and the reimbursement reminder toggle in database/045. Plan amount and optional followUp fields save under the same user lock/transaction; omitted followUp preserves existing metadata. Pending summaries group by original currency across pages/status tabs (or the specified original transaction). Reminder amounts use remaining reimbursement only and disappear when no amount remains. Transaction keyword/category/absolute-amount filters must share SQL between rows and totals, before pagination; preserve signed recovery amounts in totals.
- Investment detail is local-first. Fetch redemption fees separately at `/api/finance/investments/fund-redeem-fees`; retain full transaction history for chart markers/profit calculations. `useAsyncSection` and pagination guard against stale responses.
- `KeyedTtlCache` uses 256 fixed stripe locks + LRU eviction; TTL starts after loading completes. Polling callbacks must return their Promise for overlap protection and respect KeepAlive activation.

## Environment Variables (Frontend)

In `web/.env` or shell:

- `VITE_API_BASE_URL` — default API base (fallback: <http://localhost:8081>)
- `VITE_AUTH_API_BASE_URL`, `VITE_FINANCE_API_BASE_URL`, `VITE_TOOL_API_BASE_URL`, `VITE_FOOD_API_BASE_URL`, `VITE_MARKET_API_BASE_URL`

## Cloudflare Tunnel (External Access)

Named tunnel `bookkeeping` is configured: <https://luotianxu.dpdns.org> → <http://localhost:4173> (Vite preview). Config at `~/.cloudflared/config.yml` (protocol auto: QUIC 优先，实测 2026-10-30 UDP 已通、边缘注册到 lax；UDP 被限制时自动回退 http2). Start with `cloudflared tunnel run bookkeeping`. See README "外网访问（Cloudflare Tunnel）" for details. `vite.config.ts` `preview.allowedHosts` must include `luotianxu.dpdns.org`; preview 静态资源由 `previewCacheHeaders` 插件下发长缓存头（immutable），HTML 不缓存。
