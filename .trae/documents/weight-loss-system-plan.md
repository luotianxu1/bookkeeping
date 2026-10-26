# 减肥减脂系统实现计划

## Context（背景）

现有 `/tools/health` 健康管理页只做纯前端"体重 × 固定系数"估算，无持久化、无目标管理。用户希望在此基础上设计一个减肥减脂系统：目标设置、每日热量/营养目标（科学公式）、体重打卡、趋势进度。

**范围假设**（审批时可调整）：
- 全栈实现（后端持久化 + 前端页面）
- 归属 **tool-service**（健康管理属工具类，复用 anniversary 分层惯例）
- 不含运动记录、不与 food-service 点餐联动（后续可扩展）

## 一、数据库 `database/025_create_weight_goal_and_records.mysql.sql`

**weight_goals**（一人一条）：`id` PK、`user_id`（UNIQUE，FK users(id)）、`gender` TINYINT(1男2女)、`age` INT、`height_cm` DECIMAL(5,1)、`current_weight` DECIMAL(5,1)（起始体重）、`target_weight` DECIMAL(5,1)、`activity_level` VARCHAR(20)、`weekly_rate` DECIMAL(4,2)（kg/周）、`created_at`/`updated_at` DATETIME(3)。InnoDB utf8mb4，风格同 024。

**weight_records**：`id` PK、`user_id`（FK）、`record_date` DATE、`weight` DECIMAL(5,1)、`remark` VARCHAR(500) NULL、时间字段；`UNIQUE KEY uk_weight_records_user_date (user_id, record_date)`。

无迁移框架，手工执行 SQL。

## 二、后端（tool-service，包 com.example.tool）

新增文件（参考 [AnniversaryEntity](file:///Users/luotianxu/Documents/New project/spring-cloud-bookkeeping/tool-service/src/main/java/com/example/tool/entity/AnniversaryEntity.java)、AnniversaryService/Controller 模式）：

| 文件 | 职责 |
|---|---|
| `entity/WeightGoalEntity.java`、`WeightRecordEntity.java` | MyBatis-Plus 映射；**remark 必须 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`**（项目约定，否则清空备注不生效） |
| `mapper/WeightGoalMapper.java`、`WeightRecordMapper.java` | 仅继承 BaseMapper |
| `dto/WeightGoalRequest`、`WeightGoalResponse`、`WeightRecordRequest`、`WeightRecordResponse`、`HealthPlanResponse` | 校验注解 + 计算结果载体 |
| `service/HealthCalculator.java` | 纯计算：BMR(Mifflin-St Jeor 男 10w+6.25h−5a+5 / 女 −161) → TDEE(×1.2/1.375/1.55/1.725) → 每日缺口 = 速率×7700÷7 → 热量目标 = TDEE−缺口（女<1200/男<1500 时取下限并附提示）；蛋白质 1.6–2.2×目标体重、脂肪 0.8–1×目标体重、碳水 = (热量−蛋白×4−脂肪×9)÷4（≤0 时蛋白/脂肪取下限重算并提示）；预计达成日期 |
| `service/WeightGoalService.java` | 按 user_id upsert（唯一键兜底）；组合 HealthCalculator 填充 HealthPlanResponse |
| `service/WeightRecordService.java` | 按 user+date upsert 覆盖（捕 DuplicateKeyException 转 update）；`listRecent(userId, 30)`；`getProgress`（current=最新打卡否则起始体重，progress=(起始−current)/(起始−目标) 钳制 0–100%） |
| `controller/WeightGoalController.java` | `/api/tools/weight-goal`：`GET ?userId=`（无目标返 null）、`PUT` 保存 |
| `controller/WeightRecordController.java` | `/api/tools/weight-records`：`GET ?userId=&limit=`、`POST` 打卡 upsert |

userId 沿用项目惯例：前端 `getStoredCurrentUser()` 传参，`Result<T>` 包装。

## 三、前端（vue3-ts-app）

1. **`src/api/modules/tool.ts`**：新增类型 `WeightGoal`/`SaveWeightGoalParams`/`WeightRecord`/`SaveWeightRecordParams`/`HealthPlan` 及 `getWeightGoal`/`saveWeightGoal(PUT)`/`getWeightRecords`/`saveWeightRecord(POST)`（并入 tool.ts 符合现有惯例）。
2. **改造 [HealthPage](file:///Users/luotianxu/Documents/New project/vue3-ts-app/src/pages/tools/HealthPage/index.vue)** 为减脂主页：
   - 未设置目标 → 目标引导卡（跳 `/tools/health/goal`）+ 保留现有通用估算模块
   - 已设置目标 → 隐藏通用估算，显示减脂计划：目标卡（起始→目标、预计达成、速率）、今日热量/蛋白质目标、最新体重、进度条、打卡入口（跳 `/tools/health/records`）
3. **新增 `src/pages/tools/HealthGoalPage/`**：表单（CommonInput + SegmentedControl 选性别/活动量），校验目标体重 < 起始体重、速率 0.1–1.0 kg/周，提交后返回主页。
4. **新增 `src/pages/tools/HealthRecordPage/`**：打卡表单（日期默认今天、体重、备注）+ echarts 趋势折线（最近 30 次，markLine 标目标体重）+ 倒序历史列表。echarts 用动态 `import('echarts')`（同 PhotographyOrderOverviewPage 模式），固定高度容器、resize 监听、onBeforeUnmount dispose。
5. **`src/router/modules/tools.ts`**：注册 `/tools/health/goal`（tools-health-goal）、`/tools/health/records`（tools-health-records）。

## 四、边界情况

- 未设置目标：主页显示引导；记录页可打卡但进度显示"—"
- 同日重复打卡：upsert 覆盖（唯一键 + DuplicateKeyException 兜底）
- 目标已达成：进度 100%，提示切换维持期（按 TDEE）
- 缺口过大 / 碳水≤0：取安全下限并返回提示文案
- 备注清空：依赖 `FieldStrategy.ALWAYS`

## 五、实施顺序与验证

1. 编写并执行 025 SQL → `DESC weight_goals / weight_records` 验证
2. 后端实体/DTO/计算器/服务/控制器 → `mvn clean install -pl common,tool-service`；启动 tool-service 后 curl 验证：PUT 目标 → GET 返回计算结果；同日 POST 两次验证覆盖
3. 前端 API + 三个页面 + 路由 → `pnpm build`（vue-tsc 严格模式）
4. 浏览器回归全流程：无目标引导 → 设置目标 → 打卡 → 趋势图 → 进度
