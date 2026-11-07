# Life Hub

个人生活管理项目：记账、投资行情、实用工具（待办/摄影/旅行/健康）、餐饮管理。包含 Vue 3 移动端前端、Spring Boot 三服务后端（认证 + 业务 + 行情）和 MySQL 初始化脚本。

## 项目结构

```text
.
├── web/                         # Vue 3 + TypeScript 前端
│   ├── src/
│   │   ├── api/                 # Axios 请求封装（request.ts 含 withCredentials/401 处理）与业务 API
│   │   ├── components/          # 通用组件与 App Shell
│   │   ├── pages/               # 页面：登录、财务、工具、餐饮等；大型页面拆分为目录（如 InvestmentDetailPage/）
│   │   ├── router/              # 路由模块（全部懒加载 + prefetch.ts 空闲预取）
│   │   ├── styles/              # 全局样式
│   │   ├── types/               # TypeScript 类型
│   │   └── utils/               # 工具函数（format.ts 金额格式化、transaction-day-groups.ts 等）
│   ├── package.json
│   ├── pnpm-lock.yaml
│   └── vite.config.ts
├── life-hub/                    # Spring Boot 后端 Maven 多模块
│   ├── common/                  # 公共 DTO、JWT、安全组件与通用逻辑
│   │   └── src/main/resources/config/common-base.yml   # 各服务共享的基础配置
│   ├── auth-service/            # 认证、用户、家庭管理，端口 8081
│   ├── app-service/             # 全部业务：财务（含投资/选股）、工具、餐饮，端口 8082
│   ├── market-service/          # 行情中心（可独立部署）：A股/港股/美股/ETF/黄金/全球指数/要闻，端口 8083
│   └── pom.xml
└── database/                    # MySQL 建表与变更脚本，按数字顺序执行
```

## 统一提醒

入口为「我的 → 统一提醒」，底部「我的」显示未读数量。提醒只属于当前登录用户，家庭成员不可代读或修改。

- 覆盖未完成待办、每年纪念日、启用的固定支出（含本期扣款失败）、已创建且未还的月账单、启用的定投计划、预算预警及有预计到账日期的待报销。
- 默认提前 3 天，可设置 0–30 天并按类型开关。按北京时间计算；纪念日跨年自动滚动，2 月 29 日在非闰年按 2 月 28 日处理。
- 支持类型/未读/稍后筛选、单条或当前列表全部已读、1 小时/24 小时后提醒、恢复提醒及跳转来源。已读不等于完成；完成、取消、暂停或删除的来源会自动移出。
- 已读和稍后状态保存在服务端，按「用户 + 来源 + 记录 + 期次」隔离。改期、下一期及扣款失败会产生新的提醒，刷新不会重复生成同一期。稍后到期后恢复未读；原事项已结束则不再提醒。
- 第一版为站内提醒，在页面可见时每分钟刷新，返回标签页也会刷新；关闭网页后不发送系统推送、短信或邮件。定投显示计划日期，不把未执行自动判定为失败；还款只覆盖已有月账单，不自动生成账单。
- 发布前执行 `database/045_create_reminder_preferences.mysql.sql`，创建 `reminder_settings` 和 `reminder_states` 两张表。该脚本不改写已有业务记录，可重复执行。

接口位于业务服务 `/api/tools/reminders`，前端走 `/tool-api` 代理。读取提醒不触发扣款、记账或外部行情请求。

验证：`cd life-hub && mvn -B -ntp -pl app-service -am test -Dtest=ReminderServiceTest -Dsurefire.failIfNoSpecifiedTests=false`；前端 `cd web && pnpm test`，浏览器 `pnpm test:e2e reminders.spec.ts`（Node 22 + Chromium）。后端使用隔离 H2 执行提醒 SQL 和适配后的真实迁移，浏览器使用受控接口；不替代 MySQL 集成验证。

## 技术栈

### 前端

- Vue 3
- TypeScript
- Vite
- Vue Router
- Axios
- ECharts
- Sass
- pnpm `9.12.1`

### 后端

- Java 17
- Spring Boot `3.3.5`
- Spring Cloud `2023.0.3`
- Maven 多模块
- MyBatis-Plus
- MySQL Connector/J
- JWT
- Lombok
- Knife4j OpenAPI 文档

### 数据库

- MySQL 8.0+
- 数据库名：`bookkeeping_app`
- 字符集：`utf8mb4`
- 当前没有使用 Flyway/Liquibase，数据库脚本需要手动按文件名数字顺序执行。

## 服务端口

| 服务 | 模块 | 端口 | 说明 |
| --- | --- | --- | --- |
| 认证服务 | `auth-service` | `8081` | 登录、注册、JWT、用户/家庭管理 |
| 业务服务 | `app-service` | `8082` | 财务（账户/记账/预算/投资/选股/工资）+ 工具（联系人/待办/摄影/旅行/健康）+ 餐饮（`/api/tools/food/*`） |
| 行情中心 | `market-service` | `8083` | A股/港股/美股/ETF/黄金/全球指数实时行情与 K 线、涨跌停、市场状态、要闻、美股盘前；JWT 与 app-service 同套认证 |
| 前端 | `web` | `5173` | Vite 开发服务 |

每个后端服务启动后，Knife4j 文档地址为：

```text
http://localhost:8081/doc.html
http://localhost:8082/doc.html
http://localhost:8083/doc.html
```

## 环境要求

- JDK 17
- Maven 3.8+
- Node.js 22 LTS（CI 使用 22）
- pnpm 9.12.1
- MySQL 8.0+

可用以下命令检查：

```bash
java -version
mvn -version
node -v
pnpm -v
mysql --version
```

## 数据库初始化

先确保 MySQL 已启动，然后在项目根目录执行：

```bash
cd "/Users/luotianxu/Documents/New project"
```

首次初始化：

```bash
mysql -u root -p < database/001_create_database_and_users.mysql.sql
```

继续按数字顺序导入剩余脚本：

```bash
mysql -u root -p bookkeeping_app < database/002_create_account_types.mysql.sql
mysql -u root -p bookkeeping_app < database/003_create_accounts.mysql.sql
# 继续按编号执行尚未导入的脚本；001 已执行，无需重复。
```

如果脚本已经执行过，再次执行可能会提示表或字段已存在；这种情况需要根据当前数据库状态选择尚未执行的脚本。

## 后端启动

### 环境变量

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `JWT_SECRET` | **是（必填）** | JWT 签名密钥，至少 32 字节；**所有服务必须一致**。未设置时服务启动直接失败（配置中无默认值） |
| `DB_PASSWORD` | auth/app 必填 | MySQL `root` 用户密码；market-service 无数据库依赖 |
| `MARKET_AUTH_BASE_URL` | 按需 | 行情中心访问认证服务的地址，默认 `http://localhost:8081` |
| `INTERNAL_API_KEY` | 生产必填 | 服务间内部接口（`/api/internal/**`）鉴权密钥，**各服务必须同值**；本地开发有默认值 `dev-internal-key` |
| `AMAP_WEB_SERVICE_KEY` | 按需 | 高德 Web 服务 key，app-service 用于路线/地点代理；前端不再直调高德 |
| `AUTH_COOKIE_SECURE` | https 部署时设 `true` | 登录 Cookie 加 `Secure` 标记 |
| `KNIFE4J_PRODUCTION` | 生产建议 `true` | 设为 `true` 关闭 Knife4j 在线文档 |

### 共享配置

datasource、JWT secret、knife4j 等公共配置统一放在：

```text
life-hub/common/src/main/resources/config/common-base.yml
```

各服务 `application.yml` 通过 `spring.config.import: classpath:config/common-base.yml` 引入；服务本地同名配置优先级更高，可按需覆盖。修改数据库连接/JWT 时只改这一处。

### 命令行启动

```bash
export DB_PASSWORD='你的MySQL密码'
export JWT_SECRET='change-this-to-a-long-secret-key-at-least-32-bytes'
# 生产环境追加：
export INTERNAL_API_KEY='随机长随机串-各服务同值'
export AUTH_COOKIE_SECURE=true
export KNIFE4J_PRODUCTION=true
```

安装公共依赖并编译：

```bash
cd "/Users/luotianxu/Documents/New project/life-hub"
mvn clean install -DskipTests
```

分别启动 3 个服务：

```bash
mvn -pl auth-service spring-boot:run
mvn -pl app-service spring-boot:run
mvn -pl market-service spring-boot:run
```

也可以指定公共模块一起构建：

```bash
mvn clean install -pl common,auth-service -DskipTests
```

## IDEA 启动

建议用 IntelliJ IDEA 打开后端目录：

```text
/Users/luotianxu/Documents/New project/life-hub
```

确认 Maven 导入完成后，运行以下 Spring Boot 配置：

```text
AuthServiceApplication
AppServiceApplication
MarketServiceApplication
```

运行配置保存在本机被 Git 忽略的 `.idea/` 中，仓库不提供含密钥的预置配置。三个服务都需要 `JWT_SECRET`，auth/app 另外需要 `DB_PASSWORD`；app-service 使用地图功能时还需 `AMAP_WEB_SERVICE_KEY`。例如：

```text
DB_PASSWORD=你的MySQL密码
JWT_SECRET=change-this-to-a-long-secret-key-at-least-32-bytes
```

本机的 `.idea/workspace.xml` 已被 `.gitignore` 忽略，可以保存个人 Run Configuration，不会提交到 GitHub。

注意：IDEA 与命令行 Maven 共用 `target/classes`。若遇到 `NoClassDefFoundError`（尤其是类名无包前缀），先用 `mvn clean compile` 重建，再在 IDEA 里 Build → Rebuild Project——增量编译可能残留损坏的 class 文件。

## 前端启动

安装依赖：

```bash
cd "/Users/luotianxu/Documents/New project/web"
pnpm install
```

启动开发服务：

```bash
pnpm dev
```

默认访问：

```text
http://localhost:5173
```

构建生产包：

```bash
pnpm build
```

预览生产包：

```bash
pnpm preview
```

## 前端接口代理

Vite 已配置本地代理：

| 前端路径 | 代理到 |
| --- | --- |
| `/auth-api` | `http://localhost:8081` |
| `/finance-api` | `http://localhost:8082` |
| `/tool-api` | `http://localhost:8082` |
| `/food-api` | `http://localhost:8082` |
| `/market-api` | `http://localhost:8083` |
| `/api-proxy` | `http://localhost:8081` |

前端请求封装位于：

```text
web/src/api/request.ts
```

## 前端环境变量

可在 `web/.env`、`.env.development` 或 shell 中配置：

```text
VITE_API_BASE_URL=http://localhost:8081
VITE_AUTH_API_BASE_URL=/auth-api
VITE_FINANCE_API_BASE_URL=/finance-api
VITE_TOOL_API_BASE_URL=/tool-api
VITE_FOOD_API_BASE_URL=/food-api
VITE_MARKET_API_BASE_URL=/market-api
```

如果使用 Vite 代理，建议使用 `/auth-api`、`/finance-api` 这类相对路径。

## 鉴权与安全

### 登录态：httpOnly Cookie

JWT 通过 `access_token` httpOnly Cookie（`SameSite=Lax`）随请求携带，**不再存 localStorage**，可防 XSS 窃取：

- 登录成功由 auth-service `Set-Cookie` 下发；登出接口清除 Cookie；
- 前端 `request.ts` 全局 `withCredentials: true`，开发环境依赖 Vite 代理（同源）无需 CORS 凭证配置；
- 通过 Cloudflare Tunnel 等 https 环境访问时设置 `AUTH_COOKIE_SECURE=true`；
- 前后端必须同站部署（同域名或经同一入口代理），否则 Cookie 无法随 API 请求发送。

### 用户数据隔离

后端公共模块的 `UserIdentityScopeFilter` 统一从 JWT 注入用户身份并覆盖请求参数中的 userId，写操作强制归属本人，读操作默认本家庭范围；伪造他人 userId 的请求会被拒绝。**旧版 token（无 userId claim）一律视为未认证**，前端会收到 401 并跳转登录，属预期行为。

### 登录/注册限流

- 登录：同 IP 15 分钟窗口内连续失败会返回 429；
- 注册：同 IP 每 15 分钟最多 10 次；
- 注册接口对已存在用户名仍执行等价校验流程，不泄露用户名是否存在。

### 服务间内部接口

跨服务调用（如摄影订单入账、选股引擎日K）走 `/api/internal/**`，由 `InternalApiKeyFilter` 校验 `X-Internal-Key` 请求头，不使用 JWT。生产环境务必设置 `INTERNAL_API_KEY` 并保证各服务同值，否则使用默认弱密钥 `dev-internal-key`。

> 说明：2026-10 服务合并后，原 tool/food 与 finance 同属 app-service，`FinanceClient`/`ToolServiceClient` 已是同 JVM 本地调用；internal 接口作为兼容入口保留。同月行情代码整体迁出为独立 `market-service`（行情中心），app-service 的选股引擎经 `/api/internal/market/kline`（日K）与 `/api/internal/market/stock-list`（全市场清单，新浪 hs_a）调用行情中心，选股链路已零东财依赖。

### 行情中心（market-service）

行情中心为纯行情聚合（新浪/腾讯/东财/纳斯达克公开源），认证体系与 app-service 完全一致：

- `/api/market/**` 需登录（同一 `JWT_SECRET` 签发的 httpOnly Cookie，前端 `withCredentials` 自动携带）；`/api/internal/**` 走 `X-Internal-Key`；
- 自 2026-11-04 起不再依赖 JDBC/MySQL；用户状态与家庭范围经 auth-service 的内部接口查询，并使用短期本地缓存；
- 前端经 `/market-api` 代理访问；`JWT_SECRET`、`INTERNAL_API_KEY` 与 auth/app 同值；`market.auth-base-url`（环境变量 `MARKET_AUTH_BASE_URL`）配置认证服务地址；
- 金价主源为金投网（黄金9999 / 伦敦金 / 品牌首饰与走势），新浪作为降级源。

### 第三方 API 代理

前端不直连高德等第三方服务：地图路线/地点搜索经 app-service 的 `/api/tool/amap/*` 代理（参数白名单 + 坐标格式校验），key 只保存在服务端环境变量 `AMAP_WEB_SERVICE_KEY` 中。

## 外网访问（Cloudflare Tunnel）

### 命名隧道（固定域名，已配置）

前端已通过命名隧道绑定固定域名：

```text
https://luotianxu.dpdns.org  →  http://localhost:4173（Vite preview）
```

配置信息：

| 项目 | 值 |
| --- | --- |
| 隧道名称 | `bookkeeping` |
| 隧道 ID | `e4c7f515-1bc2-4427-b905-202919103e51` |
| 公网域名 | `luotianxu.dpdns.org` |
| 回源地址 | `http://localhost:4173` |
| 协议 | `auto`（QUIC 优先，网络限制时回退 http2） |
| 配置文件 | `~/.cloudflared/config.yml` |
| 凭证文件 | `~/.cloudflared/e4c7f515-1bc2-4427-b905-202919103e51.json` |

日常使用：

```bash
# 启动隧道（需先启动前端 preview 和后端服务）
pnpm tunnel                     # 在 web 目录下
cloudflared tunnel run bookkeeping   # 或直接用 cloudflared（隧道名仍为 bookkeeping，不影响功能）

# 查看隧道状态
cloudflared tunnel list
```

API 请求无需单独暴露：前端页面经 Vite 代理（`/auth-api` 等）转发到本地 8081-8083，隧道只暴露 4173 一个入口。

开机自启（可选）：

```bash
sudo cloudflared service install
```

### 快速隧道（临时使用，无需登录）

```bash
cloudflared tunnel --url http://localhost:4173 --protocol http2
```

会随机分配一个 `xxx.trycloudflare.com` 地址，重启后失效。

注意：`vite.config.ts` 的 `preview.allowedHosts` 目前只允许 `luotianxu.dpdns.org`；使用临时隧道时，需添加该次分配的主机名。预览服务对带 hash 的静态资源设置长期 immutable 缓存，HTML 不缓存。

## 数据一致性与加载策略

- 账务写入先在事务内锁定 `users` 对应行，再读取与修改账户、流水或持仓；普通记账、投资、借还款、人情往来、自动扣费与摄影入账共用此约定。不同用户可并发，同一用户的写入串行；现金增减使用原子 SQL。
- 基金/股票报价和赎回费率在写事务外预取；同步任务在锁内重读持仓/交易状态，避免覆盖手动交易或重复确认、分红。定投和自动扣费也在锁内复核执行日期。
- 投资详情先返回本地数据；费率通过 `/api/finance/investments/fund-redeem-fees?code=000001` 单独读取。前端详情、完整交易历史、分页列表、计划、账户、行情与费率分别维护加载与错误状态，支持局部重试。完整历史保留用于交易标记和累计收益计算。
- 分析查询在 SQL 层限定所选月/年；分页总数与收支合计共享一次聚合查询。`InvestmentFundFeeService` 与 `InvestmentPositionAccounting` 分别承担费率/FIFO 和持仓数值计算。
- `common.util.KeyedTtlCache` 使用 256 个固定分段锁及 LRU 容量淘汰，成功/失败 TTL 从回源结束开始计时；失败时保留旧值或负缓存。哈希落在同一分段的 key 会串行回源。
- `useVisibilityPolling` 在隐藏标签页或 KeepAlive 页面停用时暂停刷新，恢复时刷新；异步回调需返回 Promise，以防同一轮询任务重叠。

## 测试与 CI

后端使用 JUnit 5、Mockito 与独立 H2 内存库，覆盖资金回滚/并发、持仓买卖/结算、重复分红/扣费、FIFO 费率、缓存和身份范围。测试使用生产 Mapper 与事务，不依赖开发库或真实行情接口。另有真实 MySQL 8 集成测试，使用仓库 SQL 中的建表语句，验证并发转账/买入/建仓、跨用户并发创建同一产品、幂等重放、回滚、余额聚合、外键与 CHECK 约束，以及 044 迁移的历史冲突检查和重复执行。

```bash
cd life-hub
mvn -B -ntp clean verify
# 只验证公共模块和业务模块：
mvn test -pl common,app-service -am
# 指定独立的 MySQL 8 测试实例（账号需有创建/删除测试 schema 的权限）：
MYSQL_TEST_URL='jdbc:mysql://127.0.0.1:13306/?allowPublicKeyRetrieval=true&useSSL=false' \
MYSQL_TEST_USER=root MYSQL_TEST_PASSWORD=your-test-password mvn -B -ntp clean verify
```

未设置 `MYSQL_TEST_URL` 时跳过 MySQL 集成测试；启用后要求 MySQL 8，每次创建随机 `life_hub_test_*` 库，结束删除。初始化只提取建表语句，再在测试库执行 044 / 046 原始迁移，不执行脚本中的 `USE bookkeeping_app` 或业务种子数据。请指定独立测试实例。

044 的兼容性回归可以单独针对 MySQL 5.7 或 8.0+ 测试实例运行：在设置上述测试连接后执行 `mvn -pl common,app-service -am test '-Dtest=MySqlAccountingTest#productIdentityMigrationRejectsDuplicatesAndCanBeRetried' -Dsurefire.failIfNoSpecifiedTests=false`。此检查仅覆盖该迁移；项目完整账务测试仍要求 MySQL 8。

前端使用 Vitest + Vue Test Utils + jsdom，验证 KeepAlive/可见性轮询、独立区域失败重试、取消请求、分页旧响应隔离及提交 ID 生命周期；基金/股票/黄金建仓和黄金卖出失败重试还验证请求 ID 与自动交易时间保持一致。Playwright 在真实 Chromium 中运行生产构建，使用受控 API 响应覆盖登录、详情先显示、费率重试、交易断网重试、持仓切换、401/503 处理和图表拆包。浏览器测试不依赖在线行情或开发后端。

```bash
cd web
pnpm install --frozen-lockfile
pnpm test
pnpm build
# Node 22，首次运行需安装浏览器：
pnpm exec playwright install chromium
pnpm test:e2e
# 真实前后端 HTTP 联通测试，另需 Java 17 + Maven：
pnpm test:e2e:live
```

`test:e2e` 自动构建并在 `127.0.0.1:4175` 启动临时 preview。失败时生成 `web/playwright-report/` 和 `web/test-results/`。

`test:e2e:live` 使用真实 HTTP，不拦截或伪造认证、账户及交易 API 响应。覆盖浏览器登录写入 httpOnly/SameSite Cookie、生产记账页面提交、并发重放只扣款一次、同 ID 改参拒绝、家庭读取范围、写入身份归一化、参数校验、无效 Cookie 和登出后的 401。请求经过生产 Controller、Service、Mapper、Spring Security 与事务代理。

该命令自动通过 Maven `live-e2e` profile 构建仅供测试的 `life-hub/live-tests` 模块，在 `127.0.0.1:18881/18882` 启动认证/业务服务上下文，在 `127.0.0.1:4176` 提供独立前端构建。每次生成全新 H2 内存库，使用固定测试身份与数据，退出时关闭服务和数据库；端口已占用时直接失败，不复用现有实例。测试入口不加载业务 application.yml、不启用定时任务，所有前端代理均指向临时实例；行情服务不启动，金价预热收到本地 404 后按现有逻辑降级。H2 表用于 HTTP 链路验证，MySQL 专有约束仍由上面的独立 MySQL 测试覆盖。失败报告位于 `web/playwright-live-report/`，trace 位于 `web/test-results/`。

`.github/workflows/ci.yml` 在 push / pull request 时执行 Java 17 + MySQL 8.4 的全模块 clean verify，以及 Node 22 + pnpm 9.12.1 的冻结安装、单元测试、生产构建、受控 API 浏览器回归和真实 HTTP 联通测试。失败时上传浏览器截图和 trace；无需开发环境凭据或行情密钥。

## 交易可靠性与图表加载

- 部署本轮代码前按顺序执行 `mysql -u root -p bookkeeping_app < database/043_create_accounting_requests.mysql.sql` 和 `mysql -u root -p bookkeeping_app < database/044_unique_investment_product_identity.mysql.sql`。043 新表记录交易请求 ID、请求摘要及原始响应；044 增加归一化产品身份唯一索引。
- 执行 044 时暂停产品/持仓写入。脚本将代码、市场的首尾空格与大小写统一，并将 NULL、空串和纯空格市场视为同一市场；发现历史重复产品时列出全部冲突 ID 并停止，不自动删除或合并。需先核对这些产品及关联持仓，处理冲突后重新执行；成功后可重复执行。
- 044 使用兼容 MySQL 5.7 / 8.0+ 的分组关联查询检查重复产品。若旧版脚本在 `PARTITION BY` 附近报 1064，请重新执行修正后的完整脚本（包含开头的 `DROP PROCEDURE IF EXISTS`）；该语法错误发生在创建存储过程时，尚未执行表结构变更。项目整体数据库要求仍为 MySQL 8.0+。
- 普通记账/转账、投资买卖（含黄金卖出）及基金/股票/黄金建仓支持可选 `requestId`（16–64 位字母、数字、下划线或连字符）。请求键按用户和操作隔离，在用户锁及账务事务内写入；同键同参数返回原响应，同键不同参数拒绝，回滚不占用键。已提交投资请求的重放不依赖行情服务。旧客户端不传 ID 时保持兼容。
- 前端表单在本次页面生命周期内为未变化的失败提交复用 ID，自动生成的 `tradeAt` 随 ID 保留，成功或修改参数后生成新 ID 和时间。刷新页面会重建表单状态；不提供跨刷新自动重试。幂等记录默认保留，删除记录会失去对应历史请求的重放保护。
- 产品由数据库唯一约束处理并发创建，重复请求复用产品并保留原有名称、行情与分红数据。新产品与持仓、扣款、幂等响应在同一事务内提交或回滚；行情获取在事务外完成。
- 投资详情按持仓所属用户校验家庭可读范围；修改和删除只允许本人。金价在账务事务前读取，不可用时使用本地估值。基金/股票行情按账户锁内更新、一次 SQL 汇总，避免逐持仓重复聚合。
- 用户状态最多信任 30 秒。无法重新验证时返回 `503` 与 `Retry-After: 5`，前端保留登录；明确无效的凭据仍返回 `401`。auth 内部状态接口直查，避免与 market 本地缓存叠加。
- 行情服务的家庭范围最多信任 60 秒，以最后一次成功验证为准。过期且查询失败时仅本人可读，10 秒失败退避不能续期旧成员权限；查询恢复后使用最新范围。权限缓存测试覆盖过期边界、连续失败、恢复后的成员移除及不同用户隔离。
- `utils/echarts.ts` 仅加载普通折线；统计页面使用 `echarts-statistics.ts`，投资标记/缩放使用 `echarts-investment.ts`，K 线使用 `echarts-kline.ts`。这些入口动态加载，Vite 不再把所有图表强制合并成一个包。

## 常见问题

### 后端启动提示 Access denied

如果看到类似错误：

```text
Access denied for user 'root'@'localhost'
```

通常是 `DB_PASSWORD` 没有设置，或密码和本机 MySQL 不一致。

### 后端启动提示 Could not resolve placeholder 'JWT_SECRET'

`JWT_SECRET` 现为必填项（配置中无默认值），为三个后端服务的运行配置都设置该环境变量。

### 登录后接口 401

确认：

- `auth-service` 已启动，且三个后端服务使用同一个 `JWT_SECRET`；
- 前后端同站访问（走 Vite 代理或同一域名），Cookie 才能随请求携带；
- https 访问时已设置 `AUTH_COOKIE_SECURE=true`；
- 旧版本登录留下的 localStorage token 已失效属预期——重新登录即可，系统会自动改用 Cookie。

### NoClassDefFoundError / 类名无包前缀的 ClassNotFoundException

IDEA 增量编译可能把损坏的 class 写进 `target/classes`（字节码内含 "Unresolved compilation problems"）。执行 `mvn clean compile`，并在 IDEA 中 Build → Rebuild Project。

### 服务与包结构（2026-10 合并更名后）

后端有三个服务：`auth-service`（认证）、`app-service`（全部业务，原 finance-service/tool-service/food-service 合并更名而来）、`market-service`（行情中心，2026-10 自 app-service 迁出）。业务包结构：财务 `com.example.app`、工具 `com.example.tool`（路由 `/api/tools/*`）、餐饮 `com.example.food`（路由 `/api/tools/food/*`）。前端目录为 `web/`（原 `vue3-ts-app`）。

### 生产部署检查清单

- [ ] 三个服务设置 `JWT_SECRET`（同值，≥32 字节）；auth-service/app-service 另设 `DB_PASSWORD`
- [ ] 各服务设置 `INTERNAL_API_KEY`（同值，替换默认弱密钥）
- [ ] https 环境设置 `AUTH_COOKIE_SECURE=true`
- [ ] 设置 `KNIFE4J_PRODUCTION=true` 关闭接口文档
- [ ] app-service 设置 `AMAP_WEB_SERVICE_KEY`（用到地图功能时）
- [ ] 前后端经同一域名入口暴露（Cookie 依赖同站）

## Git

远程仓库：

```text
https://github.com/luotianxu1/bookkeeping.git
```

常用命令：

```bash
git status
git add .
git commit -m "描述本次修改"
git push
```

## 分类预算与退款报销（2026-11）

部署此版本前，在已执行 045 的数据库执行 `database/046_category_budgets_and_expense_recoveries.mysql.sql`，然后更新 app-service 和前端。046 新建分类预算表、扩展流水关联字段和金额约束、增加预算提醒开关；执行前备份并暂停账务写入。该迁移兼容 MySQL 5.7 / 8.0.16+，支持首次执行、部分失败后补齐和重复执行，保留原流水、余额、预算及提醒设置；这不改变项目整体要求 MySQL 8 的范围。MySQL 8 使用 CHECK，5.7 使用 INSERT/UPDATE 触发器执行相同的金额校验。脚本不内置 `USE`，以连接时选中的数据库为准。

若升级后出现 `Unknown column 'original_transaction_id' in 'field list'`，说明业务库的 046 迁移未完整执行。旧脚本在 MySQL 5.7 会因 `DROP CHECK` 报语法错误；即使分类预算表已建好，也可以用修正后的完整脚本重跑，不要删除已有表或数据。在项目根目录执行：

```bash
mysql -u root -p bookkeeping_app < database/046_category_budgets_and_expense_recoveries.mysql.sql
```

输入数据库密码，确认脚本成功后再启动业务服务并刷新页面。若提示先执行 045，请先执行 `database/045_create_reminder_preferences.mysql.sql`，然后重跑 046。若提示历史金额不合法，核对相应数据后重跑；脚本不会自动修正财务记录。

- **预算管理** `/finance/budgets`：切换月份管理月总预算及具体支出分类预算。分类选择最末级分类（有子分类时选择二级），仅统计该分类、该月份、CNY 现金账户的净支出。分类预算独立于月总预算，不重复累加；同月同分类保存即更新。月总预算仍包含债务还款、人情送出等现金支出，并按预算币种统计。
- **统一提醒**：当前月份的月总预算、分类预算达到 80% / 100% 时分别产生提醒，100% 阶段替换当前 80% 提醒。每个阶段的已读、稍后状态独立保存；刷新不重复提醒，退款后低于阈值自动消失，下月重新计算。提醒设置可关闭“预算预警与超支”。这是站内提醒，随页面刷新/轮询更新。
- **退款与报销** `/finance/expense-recoveries`：财务“更多功能”和本人原支出的“退款 / 报销”均可进入。分页查看全部支出、待报销、部分到账、全部到账；设置计划报销总额，分次登记退款/报销到账，查看原支出和每笔到账记录。计划设为 0 可取消尚未到账的报销；计划不能小于已报销到账金额。
- **账务口径**：到账增加所选同币种现金账户余额，作为负支出按到账时间冲减原支出分类，不计普通收入、不追溯改写原月份。例如 1 月支出 300 元、2 月退款 100 元，则 1 月支出仍为 300 元，2 月净支出减少 100 元；仅有退款的月份可以出现负净支出。统计和预算使用相同口径，分析分类列表保留负值，饼图占比只计算净支出为正的分类。未到账的报销计划不影响余额、收支或预算。
- **金额保护**：退款累计＋计划报销总额不得超过原支出；实际报销到账不得超过计划。若退款占用了待报销部分，先降低报销计划再登记退款。关联到账存在或计划未取消时，禁止修改/删除原支出。撤销到账会扣回现金并恢复支出统计和待报销金额；现金余额不足则整体回滚。已有分类预算的分类需先移除相关月份预算才能删除。
- **重试与权限**：到账沿用用户账务行锁和 `requestId` 幂等事务；同请求重试只入账一次，参数变化不能沿用旧 ID。退款、报销和分类预算接口只处理登录本人数据，家庭查看权限不赋予操作权限。

验证：`pnpm test`、`pnpm build`、`pnpm test:e2e finance-recoveries.spec.ts reminders.spec.ts transaction-search.spec.ts`、`pnpm test:e2e:live`，以及 `mvn -B -ntp -pl common,app-service -am test`。隔离 H2 测试覆盖并发限额、重试、撤销回滚、归属校验、跨月净支出、分类预算和提醒阶段；MySQL 集成测试会在提供 `MYSQL_TEST_URL` 时执行真实迁移与约束验证，H2 不能替代该检查。046 / 047 的兼容性回归可以单独针对隔离的 MySQL 5.7 / 8 实例运行：`mvn -pl common,app-service -am test '-Dtest=MySqlAccountingTest#recoveryMigrationSupportsFreshAndPartialSchemasWithoutLosingData' -Dsurefire.failIfNoSpecifiedTests=false`。该测试验证部分迁移补齐、重跑后记录、余额和跟进设置不变，以及 INSERT/UPDATE 金额约束；完整 MySQL 账务集成仍需 MySQL 8。

## 流水检索与报销跟进

- 收支列表支持名称/备注关键词搜索，分类（一级分类包含子分类）、金额范围与现有账户、日期、收入/支出筛选可组合。选择“全部时间”可搜索历史记录。金额范围按绝对值和原币数值匹配，退款仍按负支出汇总；筛选、合计和分页使用同一组数据库条件，变更条件回到第一页。
- 报销计划可选填报销对象、预计到账日期。待报销汇总包含未到账与部分到账记录，按币种分别显示合计、笔数和逾期金额，不受列表分页或状态标签影响；从原支出进入时只汇总该笔支出。
- 预计到账日期使用北京时间和统一提醒的提前天数，逾期未到账继续显示提醒。部分到账后提醒金额更新，全部到账或撤销待报销后提醒消失；改期形成新的提醒，支持独立的“报销待到账”开关。通知仅在站内展示。
- 部署前先执行 046，再执行 `database/047_reimbursement_followups.mysql.sql`。047 可重复执行，新增跟进表和报销提醒开关，不修改原有财务金额。旧客户端仅提交计划金额时保留已有跟进信息；新表单可清空对象和预计日期。当前版本不包含附件。
