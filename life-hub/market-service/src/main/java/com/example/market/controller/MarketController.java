package com.example.market.controller;

import com.example.common.result.Result;
import com.example.market.market.ActivityService;
import com.example.market.market.ExtrasService;
import com.example.market.market.F10Service;
import com.example.market.market.KlineService;
import com.example.market.market.QuotesService;
import com.example.market.market.SearchService;
import com.example.market.market.SinaClient;
import com.example.market.market.TrendService;
import com.example.market.market.TurnoverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 行情 API（移植自 investment 项目 backend/app/api/market.py，薄层：参数校验与响应组装，业务在 market 包 services）。
 *
 * <p>数据源：新浪财经（主）、腾讯财经（海外 K 线/快照）、纳斯达克官方（美股全市场）、
 * 港交所（港股代码池）、沪深交易所（融资融券）。行情行使用中文键 Map，与前端 TS 类型一一对应。</p>
 */
@RestController
@RequestMapping("/api/market")
@Tag(name = "行情中心", description = "A股/港股/美股/ETF/黄金/全球模块行情：新浪主源 + 腾讯/纳斯达克/交易所官方补充")
public class MarketController {

    private static final Set<String> KINDS = Set.of("index", "stock", "global", "hk", "us", "gold", "module");
    private static final Set<String> QUOTE_GROUPS = Set.of("global", "hk", "us", "etf", "gold");
    private static final Set<String> ACTIVITY_MARKETS = Set.of("a", "hk", "us");
    private static final Set<String> MOVER_MARKETS = Set.of("hk", "us");
    private static final Set<String> OVERSEAS_SCALES = Set.of("240", "week", "month");

    // ---------------------------------------------------------------- 基础

    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public Result<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        return Result.ok(body);
    }

    @GetMapping("/global/modules")
    @Operation(summary = "全球页六大模块行情",
        description = "A股指数/全球指数/股指期货/汇率指数/债券期货/全球商品")
    public Result<Map<String, Object>> globalModules() {
        List<Map<String, Object>> modules = QuotesService.globalModules();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", modules.stream().mapToInt(m -> ((List<?>) m.get("rows")).size()).sum());
        body.put("modules", modules);
        return Result.ok(body);
    }

    // ---------------------------------------------------------------- 市场概况

    @GetMapping("/activity")
    @Operation(summary = "市场概况", description = "涨跌家数 + 11 档涨跌分布（60 秒缓存；a/hk/us 均为全市场口径）")
    public Result<Map<String, Object>> marketActivity(
        @RequestParam(name = "market", defaultValue = "a") String market
    ) {
        require(market, ACTIVITY_MARKETS, "未知市场: " + market);
        return Result.ok(wrap(() -> ActivityService.marketActivity(market), "获取市场概况失败"));
    }

    @GetMapping("/limits")
    @Operation(summary = "A股涨跌停个股明细", description = "复用 /activity 缓存；up/down 各按成交额降序")
    public Result<Map<String, Object>> marketLimits() {
        return Result.ok(wrap(ActivityService::marketLimits, "获取涨跌停明细失败"));
    }

    @GetMapping("/movers")
    @Operation(summary = "港股/美股涨跌幅榜", description = "复用 /activity 缓存；领涨/领跌 TOP15，带流动性门槛")
    public Result<Map<String, Object>> marketMovers(
        @RequestParam(name = "market", defaultValue = "hk") String market
    ) {
        require(market, MOVER_MARKETS, "未知市场: " + market);
        return Result.ok(wrap(() -> ActivityService.marketMovers(market), "获取涨跌幅榜失败"));
    }

    @GetMapping("/sector/us")
    @Operation(summary = "美股行业板块聚合", description = "纳斯达克 screener sector：平均涨跌幅/家数/成交额，按涨跌幅降序")
    public Result<Map<String, Object>> usSectorSpot() {
        return Result.ok(wrap(() -> {
            List<Map<String, Object>> data = ActivityService.usSectors();
            if (data.isEmpty()) {
                throw new IllegalStateException("未获取到美股行业数据");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("count", data.size());
            body.put("data", data);
            return body;
        }, "获取美股行业板块失败"));
    }

    @GetMapping("/turnover")
    @Operation(summary = "今日实时成交额 + 较上一日同时点",
        description = "60 秒缓存；美股同时点对比由本服务采样留存")
    public Result<Map<String, Object>> marketTurnover(
        @RequestParam(name = "market", defaultValue = "a") String market
    ) {
        require(market, ACTIVITY_MARKETS, "未知市场: " + market);
        return Result.ok(wrap(() -> TurnoverService.marketTurnover(market), "获取成交额失败"));
    }

    // ---------------------------------------------------------------- 指数/板块/行情

    @GetMapping("/index/spot")
    @Operation(summary = "大盘核心指数实时行情", description = "新浪财经")
    public Result<Map<String, Object>> indexSpot() {
        return Result.ok(wrap(() -> {
            List<SinaClient.QuoteRow> data = QuotesService.indexSpot();
            if (data.isEmpty()) {
                throw new IllegalStateException("新浪接口未返回数据");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("count", data.size());
            body.put("data", data);
            body.put("summary", QuotesService.buildSummary(data));
            return body;
        }, "获取大盘指数失败"));
    }

    @GetMapping("/sector/spot")
    @Operation(summary = "新浪板块实时行情", description = "industry=行业板块 / concept=概念板块，按涨跌幅降序")
    public Result<Map<String, Object>> sectorSpot(
        @RequestParam(name = "kind", defaultValue = "industry") String kind
    ) {
        require(kind, Set.of("industry", "concept"), "未知板块类型: " + kind);
        return Result.ok(wrap(() -> {
            List<Map<String, Object>> data = QuotesService.sectorSpot(kind);
            if (data.isEmpty()) {
                throw new IllegalStateException("新浪接口未返回数据");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("kind", kind);
            body.put("count", data.size());
            body.put("data", data);
            return body;
        }, "获取板块行情失败"));
    }

    @GetMapping("/quotes/{group}")
    @Operation(summary = "各市场行情", description = "新浪批量接口：global / hk / us / etf / gold")
    public Result<Map<String, Object>> marketQuotes(@PathVariable("group") String group) {
        require(group, QUOTE_GROUPS, "未知市场分组: " + group);
        return Result.ok(wrap(() -> {
            List<Map<String, Object>> data = QuotesService.marketQuotes(group);
            if (data.isEmpty()) {
                throw new IllegalStateException("新浪接口未返回数据");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("group", group);
            body.put("count", data.size());
            body.put("data", data);
            return body;
        }, "获取" + group + "行情失败"));
    }

    @GetMapping("/stock/spot")
    @Operation(summary = "A股实时行情快照", description = "新浪财经，按成交额降序，默认返回前 50 条")
    public Result<Map<String, Object>> stockSpot(
        @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        int safe = Math.max(1, Math.min(5000, limit));
        return Result.ok(wrap(() -> {
            List<Map<String, Object>> data = QuotesService.stockSpot(safe);
            if (data.isEmpty()) {
                throw new IllegalStateException("新浪接口未返回数据");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("count", data.size());
            body.put("data", data);
            return body;
        }, "获取实时行情失败"));
    }

    // ---------------------------------------------------------------- 详情/K线/分时

    @GetMapping("/detail")
    @Operation(summary = "标的详情：实时行情 + K 线序列 + 五档盘口 + 估值",
        description = "index/stock 为 A 股，global/hk/us 为海外市场，gold 为黄金，module 为全球模块")
    public Result<Map<String, Object>> detail(
        @RequestParam(name = "kind", defaultValue = "stock") String kind,
        @RequestParam(name = "code") String code,
        @RequestParam(name = "scale", defaultValue = "240") String scale,
        @RequestParam(name = "datalen", defaultValue = "120") int datalen
    ) {
        requireKind(kind);
        validateCode(code);
        int safeDatalen = Math.max(10, Math.min(1000, datalen));
        validateScale(kind, scale);
        return Result.ok(wrap(() -> {
            Map<String, Object> data = QuotesService.detail(kind, code, scale, safeDatalen);
            if (data.get("quote") == null && ((List<?>) data.getOrDefault("kline", List.of())).isEmpty()) {
                throw new IllegalStateException("未查询到该代码的行情数据");
            }
            return data;
        }, "获取详情失败"));
    }

    @GetMapping("/kline")
    @Operation(summary = "仅K线序列", description = "详情页向前翻页加载更早历史用")
    public Result<Map<String, Object>> klineOnly(
        @RequestParam(name = "kind", defaultValue = "stock") String kind,
        @RequestParam(name = "code") String code,
        @RequestParam(name = "scale", defaultValue = "240") String scale,
        @RequestParam(name = "datalen", defaultValue = "250") int datalen
    ) {
        requireKind(kind);
        validateCode(code);
        int safeDatalen = Math.max(10, Math.min(1000, datalen));
        validateScale(kind, scale);
        return Result.ok(wrap(() -> {
            List<Map<String, Object>> rows = KlineService.kline(kind, code, scale, safeDatalen);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("kind", kind);
            body.put("code", code);
            body.put("scale", scale);
            body.put("count", rows.size());
            body.put("kline", rows);
            return body;
        }, "获取K线失败"));
    }

    @GetMapping("/kline/qfq")
    @Operation(summary = "A股前复权日K（腾讯 fqkline）",
        description = "与前端图表既有口径一致（qfq 防除权跳空）；symbol 形如 sh600000/sz000001/bj832000")
    public Result<Map<String, Object>> klineQfq(
        @RequestParam("symbol") String symbol,
        @RequestParam(name = "datalen", defaultValue = "120") int datalen
    ) {
        if (!symbol.matches("(sh|sz|bj)\\d{6}")) {
            throw new IllegalArgumentException("非法的 A 股代码: " + symbol);
        }
        int safeDatalen = Math.max(10, Math.min(640, datalen));
        return Result.ok(wrap(() -> {
            List<Map<String, Object>> rows = KlineService.aShareQfqDaily(symbol, safeDatalen);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("symbol", symbol);
            body.put("count", rows.size());
            body.put("kline", rows);
            return body;
        }, "获取前复权K线失败"));
    }

    @GetMapping("/trend")
    @Operation(summary = "分时（当日 1 分钟线）",
        description = "价格线 + 均价线 + 昨收基准（A股新浪 / 海外腾讯 / 美股纳斯达克官方）")
    public Result<Map<String, Object>> trend(
        @RequestParam(name = "kind", defaultValue = "stock") String kind,
        @RequestParam(name = "code") String code
    ) {
        requireKind(kind);
        validateCode(code);
        return Result.ok(wrap(() -> TrendService.trend(kind, code), "获取分时失败"));
    }

    @GetMapping("/trend/history")
    @Operation(summary = "近 N 个交易日分时回看", description = "新浪 5 分钟K线按日分组，仅 A 股")
    public Result<Map<String, Object>> trendHistory(
        @RequestParam(name = "kind", defaultValue = "stock") String kind,
        @RequestParam(name = "code") String code,
        @RequestParam(name = "days", defaultValue = "5") int days
    ) {
        requireKind(kind);
        validateCode(code);
        int safeDays = Math.max(2, Math.min(10, days));
        return Result.ok(wrap(() -> TrendService.trendHistory(kind, code, safeDays), "获取分时回看失败"));
    }

    // ---------------------------------------------------------------- 详情页扩展信息

    @GetMapping("/extra")
    @Operation(summary = "详情页扩展信息", description = "资金流向（近10日）/ 公司公告 / 分红送配（新浪源，仅 A 股）")
    public Result<Map<String, Object>> extraInfo(
        @RequestParam(name = "kind", defaultValue = "stock") String kind,
        @RequestParam(name = "code") String code
    ) {
        requireKind(kind);
        validateCode(code);
        return Result.ok(wrap(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("moneyflow", ExtrasService.moneyflow(kind, code));
            body.put("announcements", ExtrasService.announcements(kind, code));
            body.put("dividends", ExtrasService.dividends(kind, code));
            return body;
        }, "获取扩展信息失败"));
    }

    @GetMapping("/f10")
    @Operation(summary = "详情页 F10",
        description = "财务指标 / 十大流通股东（新浪）+ 融资融券（沪深交易所官方）+ 公司概况 + 所属板块（仅 A 股）")
    public Result<Map<String, Object>> f10Info(
        @RequestParam(name = "kind", defaultValue = "stock") String kind,
        @RequestParam(name = "code") String code
    ) {
        requireKind(kind);
        validateCode(code);
        Map<String, Object> body = new LinkedHashMap<>();
        if (!"stock".equals(kind)) {
            body.put("financials", null);
            body.put("holders", null);
            body.put("margin", null);
            body.put("profile", null);
            body.put("sectors", null);
            return Result.ok(body);
        }
        body.put("financials", safe(() -> F10Service.financials(code)));
        body.put("holders", safe(() -> F10Service.holders(kind, code)));
        body.put("margin", safe(() -> F10Service.margin(kind, code)));
        body.put("profile", safe(() -> F10Service.profile(kind, code)));
        body.put("sectors", safe(() -> F10Service.sectors(kind, code)));
        return Result.ok(body);
    }

    @GetMapping("/statements")
    @Operation(summary = "三大财务报表", description = "利润表/资产负债表/现金流量表，新浪源，仅 A 股")
    public Result<Map<String, Object>> statements(
        @RequestParam(name = "kind", defaultValue = "stock") String kind,
        @RequestParam(name = "code") String code,
        @RequestParam(name = "periods", defaultValue = "5") int periods
    ) {
        requireKind(kind);
        validateCode(code);
        int safePeriods = Math.max(1, Math.min(8, periods));
        return Result.ok(wrap(() -> {
            if (!"stock".equals(kind)) {
                return emptyStatements();
            }
            Map<String, Object> data = F10Service.statements(code, safePeriods);
            return data != null ? data : emptyStatements();
        }, "获取财务报表失败"));
    }

    // ---------------------------------------------------------------- 搜索

    @GetMapping("/search")
    @Operation(summary = "股票/指数/港股/美股联想搜索", description = "新浪 suggest，关键词：代码/名称/拼音")
    public Result<Map<String, Object>> search(
        @RequestParam(name = "keyword") String keyword
    ) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty() || kw.length() > 20) {
            throw new IllegalArgumentException("搜索关键词长度需在 1-20 之间");
        }
        return Result.ok(wrap(() -> {
            List<Map<String, Object>> data = SearchService.search(kw, 10);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("count", data.size());
            body.put("data", data);
            return body;
        }, "搜索失败"));
    }

    // ---------------------------------------------------------------- 内部工具

    private static void require(String value, Set<String> allowed, String message) {
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireKind(String kind) {
        require(kind, KINDS, "未知标的类型: " + kind);
    }

    private static void validateCode(String code) {
        if (code == null || code.isEmpty() || code.length() > 12
            || !code.replace("_", "").matches("[A-Za-z0-9]+")) {
            throw new IllegalArgumentException("代码仅允许字母与数字（1-12 位）");
        }
    }

    private static void validateScale(String kind, String scale) {
        boolean overseas = !(kind.equals("index") || kind.equals("stock")
            || kind.equals("gold") || kind.equals("module"));
        if (overseas && !OVERSEAS_SCALES.contains(scale)) {
            throw new IllegalArgumentException("海外/黄金仅支持日K/周K/月K");
        }
        if (!overseas && !KlineService.VALID_SCALES.contains(scale)) {
            throw new IllegalArgumentException("无效的K线周期: " + scale);
        }
    }

    private static Map<String, Object> emptyStatements() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("income", null);
        body.put("balance", null);
        body.put("cashflow", null);
        return body;
    }

    /** 上游异常统一带上业务上下文后抛出，由 GlobalExceptionHandler 转换为 Result.fail(message) */
    private static Map<String, Object> wrap(Supplier<Map<String, Object>> action, String context) {
        try {
            return action.get();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(context + ": " + e.getMessage(), e);
        }
    }

    /** F10 各子项互相独立，单项失败不影响整体（对应 Python 的 safe()） */
    private static Object safe(Supplier<Object> action) {
        try {
            return action.get();
        } catch (Exception e) {
            return null;
        }
    }
}
