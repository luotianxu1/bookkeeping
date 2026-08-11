# Bookkeeping

家庭记账与生活工具项目，包含 Vue 3 移动端前端、Spring Boot 多服务后端和 MySQL 初始化脚本。

## 项目结构

```text
.
├── vue3-ts-app/                 # Vue 3 + TypeScript 前端
│   ├── src/
│   │   ├── api/                 # Axios 请求封装与业务 API
│   │   ├── components/          # 通用组件与 App Shell
│   │   ├── pages/               # 页面：登录、财务、工具、餐饮等
│   │   ├── router/              # 路由模块
│   │   ├── styles/              # 全局样式
│   │   ├── types/               # TypeScript 类型
│   │   └── utils/               # 工具函数
│   ├── package.json
│   ├── pnpm-lock.yaml
│   └── vite.config.ts
├── spring-cloud-bookkeeping/    # Spring Boot 后端 Maven 多模块
│   ├── common/                  # 公共 DTO、JWT、安全与通用逻辑
│   ├── auth-service/            # 认证、用户、家庭管理，端口 8081
│   ├── finance-service/         # 账户、流水、投资、工资等，端口 8082
│   ├── tool-service/            # 联系人、待办、摄影订单、日历等，端口 8083
│   ├── food-service/            # 食材、菜品、菜单、点餐等，端口 8084
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
| 财务服务 | `finance-service` | `8082` | 账户、记账、预算、投资、工资等 |
| 工具服务 | `tool-service` | `8083` | 联系人、待办、摄影订单、纪念日、旅行计划等 |
| 餐饮服务 | `food-service` | `8084` | 食材、菜品、菜单、订单等 |
| 前端 | `vue3-ts-app` | `5173` | Vite 开发服务 |

每个后端服务启动后，Knife4j 文档地址为：

```text
http://localhost:8081/doc.html
http://localhost:8082/doc.html
http://localhost:8083/doc.html
http://localhost:8084/doc.html
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

后端配置通过环境变量读取数据库密码和 JWT 密钥：

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `DB_PASSWORD` | 是 | MySQL `root` 用户密码 |
| `JWT_SECRET` | 建议填写 | JWT 签名密钥，所有服务必须一致 |

命令行启动前先设置环境变量：

```bash
export DB_PASSWORD='你的MySQL密码'
export JWT_SECRET='change-this-to-a-long-secret-key-at-least-32-bytes'
```

安装公共依赖并编译：

```bash
cd "/Users/luotianxu/Documents/New project/spring-cloud-bookkeeping"
mvn clean install -DskipTests
```

分别启动 4 个服务：

```bash
mvn -pl auth-service spring-boot:run
mvn -pl finance-service spring-boot:run
mvn -pl tool-service spring-boot:run
mvn -pl food-service spring-boot:run
```

也可以指定公共模块一起构建：

```bash
mvn clean install -pl common,auth-service -DskipTests
```

## IDEA 启动

建议用 IntelliJ IDEA 打开后端目录：

```text
/Users/luotianxu/Documents/New project/spring-cloud-bookkeeping
```

确认 Maven 导入完成后，运行以下 Spring Boot 配置：

```text
AuthServiceApplication
FinanceServiceApplication
ToolServiceApplication
FoodServiceApplication
```

每个启动配置都需要带环境变量：

```text
DB_PASSWORD=你的MySQL密码
JWT_SECRET=change-this-to-a-long-secret-key-at-least-32-bytes
```

本机的 `.idea/workspace.xml` 已被 `.gitignore` 忽略，可以保存个人 Run Configuration，不会提交到 GitHub。

## 前端启动

安装依赖：

```bash
cd "/Users/luotianxu/Documents/New project/vue3-ts-app"
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
| `/tool-api` | `http://localhost:8083` |
| `/food-api` | `http://localhost:8084` |
| `/api-proxy` | `http://localhost:8081` |

前端请求封装位于：

```text
vue3-ts-app/src/api/request.ts
```

## 前端环境变量

可在 `vue3-ts-app/.env`、`.env.development` 或 shell 中配置：

```text
VITE_API_BASE_URL=http://localhost:8081
VITE_AUTH_API_BASE_URL=/auth-api
VITE_FINANCE_API_BASE_URL=/finance-api
VITE_TOOL_API_BASE_URL=/tool-api
VITE_FOOD_API_BASE_URL=/food-api
```

如果使用 Vite 代理，建议使用 `/auth-api`、`/finance-api` 这类相对路径。

## 外网临时访问

前端开发服务启动后，可以通过 Cloudflare Tunnel 暂时暴露：

```bash
cloudflared tunnel --url http://localhost:5173
```

`vite.config.ts` 已允许 `.trycloudflare.com` 作为访问域名。

## 测试

后端目前测试较少，可按模块运行：

```bash
cd "/Users/luotianxu/Documents/New project/spring-cloud-bookkeeping"
mvn test -pl tool-service
```

前端当前未配置专门的测试框架，可使用类型检查和构建验证：

```bash
cd "/Users/luotianxu/Documents/New project/vue3-ts-app"
pnpm build
```

## 常见问题

### 后端启动提示 Access denied

如果看到类似错误：

```text
Access denied for user 'root'@'localhost'
```

通常是 `DB_PASSWORD` 没有设置，或密码和本机 MySQL 不一致。

### 401 后跳回登录页

前端会在请求返回 `401` 时清理本地 JWT，并弹出登录提示或跳转到登录页。确认：

- `auth-service` 已启动
- 4 个后端服务使用同一个 `JWT_SECRET`
- 浏览器本地存储中的旧 token 已清理

### food-service 包名

`food-service` 的 Java 包名目前是 `com.example.tool`，这是现有代码结构，不要只改包名。若要重命名，需要同步调整所有引用、扫描路径和启动类。

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
