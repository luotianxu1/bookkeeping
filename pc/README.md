# Life Hub PC 管理端

独立的 Vue 3 + TypeScript + Vite 项目，使用 Vue Router、Element Plus 和 Lucide 图标。桌面布局适配 1024px 及以上屏幕；与 `web/` 分开安装、构建和部署。侧栏、顶栏使用持久化布局，菜单通过 Vue Router 仅切换功能区，功能区独立滚动；表格、表单、菜单、弹窗等统一使用 Element Plus。

## 功能模块

- 权限管理：用户管理、角色管理、权限配置，保留服务端分页、实时鉴权和授权保护。
- 财务管理：收支记账与流水、分析、预算、退款报销、固定支出、分类、工资与社保公积金、汇率。
- 资产管理：现金、债权、负债、人情、黄金、投资账户、定期存款与储蓄目标。
- 投资行情：全球/A股/港股/美股/ETF/基金/黄金、板块、K线、快讯、选股及收益测算。
- 生活工具：日历、纪念日、待办、联系人、摄影订单、旅行规划、健康与体重记录。
- 家庭餐饮：点菜、菜单订单、菜品、食材及分类维护。
- 账号与家庭：个人资料、密码与恢复码、登录设备、家庭成员、提醒设置、图标、定时任务。

功能总览可以搜索并直接打开 PC 页面。菜单源为 `src/data/modules.ts`，业务路由为 `src/router/business.ts`，页面位于 `src/business/pages/`。业务逻辑与 API 契约沿用 H5，视图在 PC 内独立实现，使用 Element Plus 表格、表单、筛选、日期选择、对话框；不加载 H5 项目或移动端缩放样式。详情与编辑路由保留原有路径，入口清单见 [docs/FEATURES.md](docs/FEATURES.md)。

所有业务入口需要 `pc.access`。业务请求仍按当前用户/家庭权限访问；管理权限不会扩大财务、工具或餐饮数据范围。定时任务入口仅面向原有管理员角色，服务端继续校验。

## 启动

使用 Node.js 22、pnpm 9.12.1、Java 17。后端复用 `life-hub/auth-service`（8081）、`app-service`（8082）及 `market-service`（8083）。

1. 新数据库按根目录 `database/` 编号顺序初始化。已运行本项目 001、050 的数据库，只需为本次功能执行新增文件：

   ```bash
   mysql -u root -p bookkeeping_app < database/051_create_admin_roles.mysql.sql
   ```

   051 只创建 `admin_roles`、`admin_role_permissions` 并补齐缺失的两个内置角色，不修改已有用户或授权。没有独立 ALTER 或历史回填脚本。

2. 按根 README 配置后端的 `DB_PASSWORD`、`JWT_SECRET`、`INTERNAL_API_KEY`，重新构建并重启后端服务。本次认证服务增加管理接口，公共认证与行情服务增加账号重新启用后的实时状态复核。普通开发环境沿用原服务启动方式，无需额外启动一个 PC 后端。

3. 在根目录执行：

   ```bash
   cd pc
   pnpm install
   pnpm dev
   ```

4. 打开 <http://localhost:5174>，使用现有 `role_name=super_admin` 的账号登录。新库的超级管理员由 `database/001` 初始化；不额外创建默认登录凭据。普通用户默认无 PC 权限。

可复制 `.env.example` 为 `.env.local`。`/auth-api` 代理到 8081；`/finance-api`、`/tool-api`、`/food-api` 代理到 8082；`/market-api` 代理到 8083。代理分别使用 `AUTH_PROXY_TARGET`、`APP_PROXY_TARGET`、`MARKET_PROXY_TARGET`。

## 角色与授权规则

- 兼容现有 `users.role_name`，本期每个用户关联一个角色；角色的编码对应该字段，不引入多角色合并规则。未知历史角色不自动获得任何 PC 权限。
- `super_admin` 和 `user` 为只读内置角色。超级管理员拥有完整 PC 权限；普通用户使用原移动端业务。
- 新建自定义角色初始无权限。在权限管理中授权后，再到用户列表分配。新建用户初始为普通用户。
- 权限目录由服务端 `AdminPermissions` 定义，只展示已经实现的接口权限；不能在页面随意创建一个没有服务端校验的权限字符串。新增业务需同时扩展服务端目录/接口校验和前端页面。
- 非超级管理员不能管理权限高于自己的用户/角色，也不能分配超级管理员角色或授予自己未持有的权限。
- 不允许修改自己的角色或账号状态，也不允许修改自己所属角色的资料/权限；内置角色不可编辑/删除；有关联用户（包括停用用户）的角色不可删除。
- 账号停用、锁定时，在用户行锁内撤销全部会话，重新启用不会复活旧会话。角色权限修改在下一次管理请求生效；停用角色仅禁止 PC 访问，不改变用户的移动端业务权限。
- 登录认证遇到缓存的停用状态时会重新查询真实状态，重新启用后的新会话可立即访问；复核服务不可用仍返回 503，不放行旧会话或延长缓存信任。
- 管理写入先串行锁定内置角色行，再按 ID 顺序获取用户行锁，锁内重新核验用户状态、当前会话与实时权限，防止撤销后的请求继续写入。
- 不硬删除用户，也不通过管理端重置密码；已有业务数据、账号安全及家庭隔离规则继续由原业务维护。

| 权限                | 功能                     |
| ------------------- | ------------------------ |
| `pc.access`         | 进入 PC 工作台及本人业务 |
| `user.read`         | 查询用户                 |
| `user.create`       | 新建普通用户             |
| `user.update`       | 编辑用户资料             |
| `user.status`       | 启用、停用、锁定用户     |
| `user.assign-role`  | 分配角色                 |
| `role.read`         | 查看角色                 |
| `role.create`       | 新建角色                 |
| `role.update`       | 修改角色资料和状态       |
| `role.delete`       | 删除未关联用户的角色     |
| `permission.read`   | 查看权限目录及授权       |
| `permission.assign` | 配置角色权限             |

## API

请求通过 `/auth-api` 代理到认证服务，使用既有 `Result<T>`（成功 `code=0`）响应，发送 Cookie，不在 localStorage/sessionStorage 保存 JWT。

| 方法         | 路径                                  | 用途                       |
| ------------ | ------------------------------------- | -------------------------- |
| POST         | `/api/auth/login`、`/api/auth/logout` | 复用原登录/退出            |
| GET          | `/api/admin/session`                  | 当前管理用户和有效权限     |
| GET / POST   | `/api/admin/users`                    | 查询/创建用户              |
| PUT          | `/api/admin/users/{id}`               | 编辑资料                   |
| PUT          | `/api/admin/users/{id}/status`        | 变更状态                   |
| PUT          | `/api/admin/users/{id}/role`          | 分配角色                   |
| GET          | `/api/admin/roles/options`            | 当前操作者可分配的启用角色 |
| GET / POST   | `/api/admin/roles`                    | 查询/创建角色              |
| PUT / DELETE | `/api/admin/roles/{id}`               | 编辑/删除角色              |
| GET          | `/api/admin/permissions`              | 服务端权限目录与依赖       |
| PUT          | `/api/admin/roles/{id}/permissions`   | 原子替换角色授权           |

## 验证

```bash
cd pc
pnpm test                 # 请求契约、Cookie 行为、权限依赖与安全跳转
pnpm build                # TypeScript + 生产构建
pnpm exec playwright install chromium
pnpm test:e2e             # Java 17 + Maven；真实认证/RBAC HTTP + 隔离 H2
pnpm test:e2e:business    # 生产前端 + 可控响应；模块入口、表单、路由、图表
pnpm test:e2e:dev         # 全新 Vite 缓存；检查全部菜单首次访问不会整页刷新
pnpm test:e2e:live        # 真实认证/业务 HTTP + 隔离 H2；记账、幂等、储蓄预留

cd ../life-hub
mvn -B -ntp test -pl auth-service,market-service -am
```

浏览器测试自动构建可选 `live-e2e` profile，启动 `PcTestServer`（127.0.0.1:18883）和生产前端（127.0.0.1:4177）；读取 001/050/051 的生产 DDL，在全新 H2 数据库中创建测试账号。登录、用户、角色和授权流程均使用生产 Controller/Service/安全链；异常测试模拟一次 503，竞态测试延迟一个真实查询响应。无业务数据库、无行情外部请求。`live-tests` 不进入部署，失败报告在 `playwright-report/`、`test-results/`。H2 验证不替代 MySQL 集成测试。

业务浏览器测试在 4178 运行，不访问后端。业务联通测试使用已有 `LiveTestServers --pc`：18881/18882 上的生产认证/业务接口、全新 H2，4179 上的生产 PC 前端；仅该测试参数会为隔离测试用户启用 PC 权限，不改 H5 测试默认身份。各套浏览器测试请顺序执行，避免同时清理 Maven 测试产物。

开发导航测试使用随机本地端口和独立临时 Vite 缓存，不访问后端。逐个点击模块菜单，检查文档请求、HMR 整页刷新消息和侧栏/顶栏实例；覆盖首次加载图表及浏览器返回。Element Plus 的按需样式入口在 `vite.config.ts` 中预编译，防止进入新页面时重新优化依赖而刷新整页。

## 部署

`pnpm build` 输出 `pc/dist/`；本地 `pnpm preview` 使用 4174 端口。生产静态服务器需将页面路由回退到 `index.html`，将 `/auth-api/` 转发到认证服务、`/finance-api/` / `/tool-api/` / `/food-api/` 转发到业务服务、`/market-api/` 转发到行情服务，并去掉各代理前缀。建议使用独立管理端域名或独立站点根路径；当前路由未配置子路径部署。

登录依赖同源代理的 httpOnly Cookie。HTTPS 部署时认证服务设置 `AUTH_COOKIE_SECURE=true`。如果 PC 与移动端使用同一域名（端口不隔离 Cookie），会共享登录 Cookie，退出当前 Cookie 会话会同时影响该域名的另一端。
