# Life Hub

个人生活管理项目：记账、投资行情、实用工具（待办/摄影/旅行/健康）、餐饮管理。包含 Vue 3 移动端前端、Spring Boot 双服务后端（认证 + 业务）和 MySQL 初始化脚本。

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
│   ├── app-service/             # 全部业务：财务（含投资/行情）、工具、餐饮，端口 8082
│   └── pom.xml
└── database/                    # MySQL 建表与变更脚本，按数字顺序执行
```

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
| 业务服务 | `app-service` | `8082` | 财务（账户/记账/预算/投资/行情/工资）+ 工具（联系人/待办/摄影/旅行/健康）+ 餐饮（`/api/tools/food/*`） |
| 前端 | `web` | `5173` | Vite 开发服务 |

每个后端服务启动后，Knife4j 文档地址为：

```text
http://localhost:8081/doc.html
http://localhost:8082/doc.html
```

## 环境要求

- JDK 17
- Maven 3.8+
- Node.js 18+
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
for f in database/[0-9][0-9][0-9]_*.mysql.sql database/[0-9][0-9][0-9]_*.sql; do
  [ -f "$f" ] && mysql -u root -p bookkeeping_app < "$f"
done
```

如果脚本已经执行过，再次执行可能会提示表或字段已存在；这种情况需要根据当前数据库状态选择尚未执行的脚本。

## 后端启动

### 环境变量

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `JWT_SECRET` | **是（必填）** | JWT 签名密钥，至少 32 字节；**所有服务必须一致**。未设置时服务启动直接失败（配置中无默认值） |
| `DB_PASSWORD` | 是 | MySQL `root` 用户密码 |
| `INTERNAL_API_KEY` | 生产必填 | 服务间内部接口（`/api/internal/**`）鉴权密钥，**两服务必须同值**；本地开发有默认值 `dev-internal-key` |
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
export INTERNAL_API_KEY='随机长随机串-四服务同值'
export AUTH_COOKIE_SECURE=true
export KNIFE4J_PRODUCTION=true
```

安装公共依赖并编译：

```bash
cd "/Users/luotianxu/Documents/New project/life-hub"
mvn clean install -DskipTests
```

分别启动 2 个服务：

```bash
mvn -pl auth-service spring-boot:run
mvn -pl app-service spring-boot:run
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
```

每个启动配置都需要带环境变量。`.idea/runConfigurations/` 中已提交预置好环境变量的运行配置（`JWT_SECRET`、`DB_PASSWORD`，app-service 另有 `AMAP_WEB_SERVICE_KEY`），直接使用即可；若自行新建配置，至少要设置：

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

跨服务调用（如摄影订单入账）走 `/api/internal/**`，由 `InternalApiKeyFilter` 校验 `X-Internal-Key` 请求头，不使用 JWT。生产环境务必设置 `INTERNAL_API_KEY` 并保证两服务同值，否则使用默认弱密钥 `dev-internal-key`。

> 说明：2026-10 服务合并后，原 tool/food 与 finance 同属 app-service，`FinanceClient`/`ToolServiceClient` 已是同 JVM 本地调用；internal 接口作为兼容入口保留。

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
| 协议 | `http2`（本机网络限制 UDP/QUIC） |
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

API 请求无需单独暴露：前端页面经 Vite 代理（`/auth-api` 等）转发到本地 8081-8082，隧道只暴露 4173 一个入口。

开机自启（可选）：

```bash
sudo cloudflared service install
```

### 快速隧道（临时使用，无需登录）

```bash
cloudflared tunnel --url http://localhost:4173 --protocol http2
```

会随机分配一个 `xxx.trycloudflare.com` 地址，重启后失效。

注意：`vite.config.ts` 的 `preview.allowedHosts` 中已加入 `luotianxu.dpdns.org` 和 `.trycloudflare.com`，否则 Vite 会以 403 拦截这些域名的请求。

## 测试

后端目前测试较少，可按模块运行：

```bash
cd "/Users/luotianxu/Documents/New project/life-hub"
mvn test -pl app-service
```

前端当前未配置专门的测试框架，可使用类型检查和构建验证：

```bash
cd "/Users/luotianxu/Documents/New project/web"
pnpm build
```

## 常见问题

### 后端启动提示 Access denied

如果看到类似错误：

```text
Access denied for user 'root'@'localhost'
```

通常是 `DB_PASSWORD` 没有设置，或密码和本机 MySQL 不一致。

### 后端启动提示 Could not resolve placeholder 'JWT_SECRET'

`JWT_SECRET` 现为必填项（配置中无默认值），为四个服务的运行配置都设置该环境变量。

### 登录后接口 401

确认：

- `auth-service` 已启动，且两个后端服务使用同一个 `JWT_SECRET`；
- 前后端同站访问（走 Vite 代理或同一域名），Cookie 才能随请求携带；
- https 访问时已设置 `AUTH_COOKIE_SECURE=true`；
- 旧版本登录留下的 localStorage token 已失效属预期——重新登录即可，系统会自动改用 Cookie。

### NoClassDefFoundError / 类名无包前缀的 ClassNotFoundException

IDEA 增量编译可能把损坏的 class 写进 `target/classes`（字节码内含 "Unresolved compilation problems"）。执行 `mvn clean compile`，并在 IDEA 中 Build → Rebuild Project。

### 服务与包结构（2026-10 合并更名后）

后端只有 `auth-service`（认证）与 `app-service`（全部业务，原 finance-service/tool-service/food-service 合并更名而来）。业务包结构：财务 `com.example.app`、工具 `com.example.tool`（路由 `/api/tools/*`）、餐饮 `com.example.food`（路由 `/api/tools/food/*`）。前端目录为 `web/`（原 `vue3-ts-app`）。

### 生产部署检查清单

- [ ] 两服务均设置 `JWT_SECRET`（同值，≥32 字节）与 `DB_PASSWORD`
- [ ] 两服务设置 `INTERNAL_API_KEY`（同值，替换默认弱密钥）
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
