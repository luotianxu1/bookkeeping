# 项目长期约定（spring-cloud-bookkeeping + vue3-ts-app）

## 前端（vue3-ts-app）硬性规范
- **rem 适配陷阱**：`utils/rem.ts` 把 html.fontSize 设为动态大值（视口/375×37.5 ≈ 39px），postcss-pxtorem（rootValue 37.5，minPixelValue 2）把 px 转 rem。body 未设 font-size → **任何依赖继承字号的元素会直接拿到 ~39px**。新增文本元素必须显式设 `font-size: var(--font-size-*)` token，或确保页面根有继承基准字号（`.market-page` 与独立的 `.detail-page` 都已各自设 14px 基准；详情页 `/finance/global-market/detail/:kind/:code` 是独立顶层路由，不在 market 外壳内）。
- **grid 溢出陷阱**：页面级 grid 的轨道默认 min-width:auto，nowrap 表格/文本的 min-content 会沿 grid 链条把整页撑出横向滚动。容纳宽内容的 grid 必须加 `> * { min-width: 0 }`，宽表格交给卡片内 `overflow-x: auto` 的包裹层滚动。
- design token 全部在 `src/styles/base.css`：涨=红 `--color-danger`、跌=绿 `--color-success`、金色点缀 `--color-chart-gold`；字号 `--font-size-11~38`、间距 `--space-2~96`、圆角 `--radius-8~36`；注意 `--space-18` 不存在。
- API：`financeRequest` 实例无路径前缀，路径须写全（如 `/api/finance/market/...`）；行情行数据用中文键 Map 保持与后端契约一致。

## 后端（spring-cloud-bookkeeping）
- finance-service 端口 8082；JWT_SECRET/DB_PASSWORD 取自 `.idea/runConfigurations/FinanceServiceApplication.xml`。
- 单模块 `mvn -pl finance-service spring-boot:run` 前需先 `mvn -pl common install`（否则 classpath:config/common-base.yml 找不到）。
- GlobalExceptionHandler 有 Exception.class 兜底：任何未匹配路由（NoResourceFoundException）都返回 HTTP 500 而非 404——排障时先看响应体里的异常类型。
