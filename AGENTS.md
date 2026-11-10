# AGENTS.md

## Project Structure

Monorepo with independent frontend and backend components:

- `web/` — Vue 3 mobile-first frontend
- `pc/` — Vue 3 + TypeScript desktop workspace (RBAC + finance/assets/market/tools/food/profile business modules)
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

## PC 管理端

- `cd pc && pnpm install && pnpm dev`；Node 22 / pnpm 9.12.1，开发端口 5174，预览 4174。复用 auth-service 的 Cookie 登录，`/auth-api` 同源代理到 8081。
- 业务路由在 `pc/src/router/business.ts`，分组菜单在 `src/data/modules.ts`，桌面业务视图在 `src/business/pages`；仅复用 H5 API 契约与逻辑，不引用移动端样式。业务以 `pc.access` 进入，继续执行本人/家庭数据隔离。业务代理 `/finance-api` / `/tool-api` / `/food-api` → 8082，`/market-api` → 8083。
- 检查：`pnpm test`、`pnpm build`、`pnpm test:e2e`。另有 `pnpm test:e2e:business`（4178，可控业务响应）和 `pnpm test:e2e:live`（18881/18882、4179，`LiveTestServers --pc` 隔离 H2）；浏览器套件顺序执行。浏览器测试依赖 Java 17 / Maven，使用可选 live-tests 的 `PcTestServer` 与隔离 H2（18883），前端预览 4177，不访问开发数据库。
- PC RBAC 位于 auth-service 的 `admin` 包；051 定义角色及权限关联，兼容 `users.role_name` 的单角色模型。内置 `super_admin`/`user` 不可变；权限目录由服务端定义，不接受任意权限键。
- 管理写入先锁 `admin_roles` 的 super_admin 行，再按用户 ID 顺序锁 users，锁内重新核验当前会话及实时权限。禁止自改角色/状态、修改自己所属角色、越权授权及删除有关联用户的角色。停用/锁定用户撤销全部会话。
- 管理权限只覆盖 `/api/admin/**`；不放开移动端的用户/家庭数据隔离。权限变更不缓存，接口始终执行服务端鉴权。
- 公共认证遇到缓存的停用状态时用 `isActiveFresh` 复核，确保重新启用的新会话可立即访问；复核故障仍返回 503，旧会话不会恢复，失败不能延长可信期。

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

- 公共收支分类使用 user_id=NULL；私人二级分类保留所属用户，记账/续费/分类预算的末级判断仅统计公共及本人的启用子分类。旧库若存在 is_system=1 且绑定用户的历史分类，补默认分类前先检查归属和引用，避免生成同名系统副本。

## Environment Variables (Frontend)

In `web/.env` or shell:

- `VITE_API_BASE_URL` — default API base (fallback: <http://localhost:8081>)
- `VITE_AUTH_API_BASE_URL`, `VITE_FINANCE_API_BASE_URL`, `VITE_TOOL_API_BASE_URL`, `VITE_FOOD_API_BASE_URL`, `VITE_MARKET_API_BASE_URL`

## Cloudflare Tunnel (External Access)

Named tunnel `bookkeeping` is configured: <https://luotianxu.dpdns.org> → <http://localhost:4173> (Vite preview). Config at `~/.cloudflared/config.yml` (protocol auto: QUIC 优先，实测 2026-10-30 UDP 已通、边缘注册到 lax；UDP 被限制时自动回退 http2). Start with `cloudflared tunnel run bookkeeping`. See README "外网访问（Cloudflare Tunnel）" for details. `vite.config.ts` `preview.allowedHosts` must include `luotianxu.dpdns.org`; preview 静态资源由 `previewCacheHeaders` 插件下发长缓存头（immutable），HTML 不缓存。

## 定期存款账务约束

- `term_deposit` 账户余额由 `term_deposits.status='active'` 的本金汇总；`accounts.current_balance` 保持 0，不把预计利息计入资产。到期仍保持 active，视图显示 due，用户确认结算后才关闭。
- 存入/本金取回/续存使用内部 transfer，只有确认的实际利息使用 income。`TD-` 前缀流水由 `TermDepositService` 独占管理，普通流水更新/删除必须拒绝。补录不生成现金流水。
- 存单可保存默认利息收入分类（049 的 interest_category_id）；结算可重新选择，有实际利息时必须提交本人或公共的启用末级收入分类。续存沿用本次所选分类，历史结算分类从对应收入流水读取，修改默认分类不改变历史流水。
- 用户账务锁、目标预留、原子余额修改和幂等响应适用于全部定期写入；撤销续存须确保后继存单尚未结清，并原子恢复本金、现金及关联流水状态。
- 002/045 的新建定义包含账户类型/提醒开关；049 创建存单与操作表。已有库须补齐 `term_deposit_enabled` 后运行新版提醒查询。新增数据库变更合并入对应 CREATE 定义，不提交独立 ALTER/同步文件。

## 账号安全

- 050 新建 user_security/user_sessions；JWT 必须带 jti 并匹配本人有效会话，旧无 jti 凭证不接受。三个服务均核验会话，market-service 经内部接口核验，不缓存成功结果，故障返回 503。
- 登录验证与会话创建、改密、恢复码生成/消费、设备退出先锁 users 对应行。敏感设置写入须在锁内复验当前会话，防止已退出请求继续更新。改密及恢复码找回撤销全部会话，退出登录撤销当前会话。
- 密码保留首尾空格，6–64 字符且 UTF-8 不超过 BCrypt 的 72 字节。恢复码用安全随机数生成，摘要存储，仅生成时展示明文；成功使用后一次性消费。密码确认与找回接口须按账号/IP 限流。
