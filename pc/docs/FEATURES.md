# 项目功能盘点

盘点时间：2026-11-10。以下按当前 `web/src/router/modules/` 的实际路由整理；列表包含功能入口及详情、编辑页面。以下业务路由均已接入 PC；菜单按七个模块组织，详情和编辑页从对应列表进入。PC 使用独立的 Element Plus 桌面视图，保留 H5 的 API 契约与数据隔离。

## PC 管理端

| 页面     | 路径           | 已实现能力                           |
| -------- | -------------- | ------------------------------------ |
| 功能总览 | `/overview`    | 七个模块的功能入口与搜索             |
| 用户管理 | `/users`       | 查询、新建、编辑、状态变更、角色分配 |
| 角色管理 | `/roles`       | 查询、新建、编辑、启停、保护性删除   |
| 权限配置 | `/permissions` | 权限目录、角色授权、依赖校验与保存   |
| 登录     | `/login`       | 现有 Cookie 会话登录                 |

## 系统、账号与家庭

来源：`web/src/router/modules/main.ts`。

| 功能页面     | 路径                       |
| ------------ | -------------------------- |
| 个人资料     | `/profile/edit`            |
| 隐私与安全   | `/profile/security`        |
| 找回密码     | `/forgot-password`         |
| 统一提醒     | `/profile/reminders`       |
| 登录         | `/login`                   |
| 注册         | `/register`                |
| 我的         | `/profile`                 |
| 绑定家庭成员 | `/profile/family-members`  |
| 任务管理     | `/profile/task-management` |
| 图标管理     | `/profile/icon-management` |

## 财务、资产与行情

来源：`web/src/router/modules/finance.ts`。

| 功能页面        | 路径                                                   |
| --------------- | ------------------------------------------------------ |
| 定期存款        | `/finance/term-deposits`                               |
| 储蓄目标        | `/finance/savings-goals`                               |
| 退款与报销      | `/finance/expense-recoveries`                          |
| 财务首页        | `/finance`                                             |
| 收支列表        | `/finance/transactions`                                |
| 收支分析        | `/finance/analysis`                                    |
| 更多功能        | `/finance/more-features`                               |
| 资产趋势        | `/finance/trend`                                       |
| 金价            | `/finance/gold-price`                                  |
| 红利策略        | `/finance/dividend-strategy`                           |
| 市场快讯        | `/finance/market-news`                                 |
| A股选股         | `/finance/stock-screener`                              |
| 美股行情        | `/finance/us-market`                                   |
| 行情中心        | `/finance/global-market`                               |
| 行情中心 · 基金 | `/finance/global-market/funds`                         |
| 基金详情        | `/finance/global-market/funds/:code`                   |
| 行情中心 · 全球 | `/finance/global-market/global`                        |
| 行情中心 · A股  | `/finance/global-market/a`                             |
| 行情中心 · 港股 | `/finance/global-market/hk`                            |
| 行情中心 · 美股 | `/finance/global-market/us`                            |
| 行情中心 · ETF  | `/finance/global-market/etf`                           |
| 行情中心 · 黄金 | `/finance/global-market/gold`                          |
| 行情中心 · 板块 | `/finance/global-market/sectors`                       |
| 行情详情        | `/finance/global-market/detail/:kind/:code`            |
| 收益预测        | `/finance/profit-forecast`                             |
| 基金收益        | `/finance/fund-profit`                                 |
| 回本预测        | `/finance/break-even-forecast`                         |
| 攒股收息        | `/finance/dividend-income`                             |
| 收息预测        | `/finance/dividend-forecast`                           |
| 财务自由测算    | `/finance/free-estimate`                               |
| 预算管理        | `/finance/budgets`                                     |
| 固定支出        | `/finance/renewals`                                    |
| 工资管理        | `/finance/salary`                                      |
| 工资设置        | `/finance/salary/settings`                             |
| 工资明细        | `/finance/salary/records`                              |
| 现金账户        | `/finance/salary/cash`                                 |
| 工资账户        | `/finance/salary/accounts/:accountType`                |
| 工资税务        | `/finance/salary/tax`                                  |
| 汇率换算        | `/finance/exchange-rate`                               |
| 记一笔-支出     | `/finance/entry/expense`                               |
| 分类管理        | `/finance/categories`                                  |
| 账户管理        | `/finance/accounts`                                    |
| 现金账户        | `/finance/accounts/cash`                               |
| 现金资产详情    | `/finance/accounts/cash/:accountId`                    |
| 债务账户        | `/finance/accounts/debt`                               |
| 已结清明细      | `/finance/accounts/debt/history`                       |
| 债务项详情      | `/finance/accounts/debt/:accountId/record/:recordId`   |
| 债务详情        | `/finance/accounts/debt/:accountId`                    |
| 负债账户        | `/finance/accounts/liability`                          |
| 负债详情        | `/finance/accounts/liability/:accountId`               |
| 人情账户        | `/finance/accounts/human-relation`                     |
| 人情详情        | `/finance/accounts/human-relation/:accountId`          |
| 黄金账户        | `/finance/accounts/gold`                               |
| 黄金账户持仓    | `/finance/accounts/gold/position`                      |
| 清仓记录        | `/finance/accounts/gold/liquidation`                   |
| 投资账户        | `/finance/accounts/investment`                         |
| 清仓明细        | `/finance/accounts/investment/:accountId/liquidations` |
| 投资详情        | `/finance/accounts/investment/:accountId`              |
| 投资资产详情    | `/finance/accounts/investment/detail`                  |

## 生活工具

来源：`web/src/router/modules/tools.ts`。

| 功能页面   | 路径                                 |
| ---------- | ------------------------------------ |
| 工具       | `/tools`                             |
| 日历       | `/tools/calendar`                    |
| 纪念日     | `/tools/anniversaries`               |
| 摄影订单   | `/tools/photography-orders`          |
| 订单总览   | `/tools/photography-orders/overview` |
| 代办事项   | `/tools/todo-items`                  |
| 联系人管理 | `/tools/contacts`                    |
| 健康管理   | `/tools/health`                      |
| 体重打卡   | `/tools/health/records`              |
| 旅行管理   | `/tools/travel-plans`                |
| 新增旅行   | `/tools/travel-plans/new`            |
| 修改旅行   | `/tools/travel-plans/:planId/edit`   |
| 旅行详情   | `/tools/travel-plans/:planId`        |

## 家庭餐饮

来源：`web/src/router/modules/food.ts`。

| 功能页面     | 路径                              |
| ------------ | --------------------------------- |
| 餐饮         | `/food`                           |
| 菜单列表     | `/food/menu`                      |
| 菜单详情     | `/food/menu/:orderId`             |
| 菜品列表     | `/food/dishes`                    |
| 新增菜品     | `/food/dishes/new`                |
| 菜品详情     | `/food/dishes/:dishId`            |
| 菜品分类     | `/food/categories`                |
| 新增菜品分类 | `/food/categories/new`            |
| 食材列表     | `/food/ingredients`               |
| 食材分类     | `/food/ingredient-categories`     |
| 新增食材分类 | `/food/ingredient-categories/new` |

## 后端对应关系

| 服务                  | 功能范围                                                                                       |
| --------------------- | ---------------------------------------------------------------------------------------------- |
| auth-service · 8081   | 登录、注册、会话、密码与恢复码、家庭绑定；PC 用户、角色和权限管理                              |
| app-service · 8082    | 财务、账户、预算、退款报销、储蓄目标、定期、投资、工资、工具、健康、旅行、餐饮、提醒、定时任务 |
| market-service · 8083 | 全球市场、A/港/美股、ETF、基金净值、K 线分时、板块、涨跌停、快讯、盘前、黄金价格、外汇行情     |

模块菜单与功能总览统一使用 `pc/src/data/modules.ts`；后续新增路由时同步更新入口与此清单。
