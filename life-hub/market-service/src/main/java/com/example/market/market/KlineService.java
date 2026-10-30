package com.example.market.market;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * K 线数据（对应 investment 项目 services/kline.py）。
 *
 * <ul>
 *   <li>A 股（指数/个股/ETF）：新浪 CN_MarketDataService，支持日 K 与分钟线；周K/月K 由日线聚合</li>
 *   <li>海外（全球指数/港股/美股）：腾讯 newfqkline，支持日K/周K/月K</li>
 *   <li>黄金模块：期货（hf_ 外盘 / nf_ 国内）走新浪期货日K；黄金ETF 等 A 股标的走新浪 K 线</li>
 * </ul>
 *
 * <p>无状态服务，直接静态方法组织（与 Python 模块函数一一对应）。</p>
 */
public final class KlineService {

    private KlineService() {
    }

    /** 黄金模块：详情短代码（新浪行情行已去前缀）→ 新浪完整代码 */
    private static final Map<String, String> GOLD_SYMBOL_MAP = buildGoldSymbolMap();

    /** 无日K历史源的全球页代码（新浪 znb_ 只有实时行情，腾讯无对应代码） */
    public static final Set<String> MODULE_NO_KLINE = Set.of("znb_NKY", "znb_UKX", "znb_DAX", "znb_CAC");

    /** 允许的 K 线周期：240 = 日K；week/month 为周K/月K */
    public static final Set<String> VALID_SCALES = Set.of("5", "15", "30", "60", "240", "week", "month");

    /** 支持 K 线的 kinds（海外 kind 只提供日 K） */
    public static final Set<String> OVERSEAS_KINDS = Set.of("global", "hk", "us");

    private static Map<String, String> buildGoldSymbolMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : MarketConfig.MARKET_GROUPS.getOrDefault("gold", List.of())) {
            String code = entry.getKey();
            String shortCode = code.startsWith("hf_") || code.startsWith("nf_")
                ? code.substring(3)
                : code.substring(2);
            map.put(shortCode, code);
        }
        return map;
    }

    /** 黄金详情短代码 → 新浪代码；未知短代码按数字=A股、字母=外盘期货推断 */
    public static String goldSinaSymbol(String code) {
        String mapped = GOLD_SYMBOL_MAP.get(code);
        if (mapped != null) {
            return mapped;
        }
        if (!code.isEmpty() && Character.isDigit(code.charAt(0))) {
            return sinaSymbol("stock", code);
        }
        return "hf_" + code;
    }

    /**
     * 把 (kind, 6位代码) 解析为新浪行情代码，如 sh000001 / sz000001 / bj899050。
     */
    public static String sinaSymbol(String kind, String code) {
        code = code.trim();
        if (kind.equals("index")) {
            for (Map.Entry<String, String> entry : MarketConfig.SINA_INDEX_CODES) {
                if (entry.getKey().endsWith(code)) {
                    return entry.getKey();
                }
            }
            return "sh" + code;
        }
        if (code.startsWith("60") || code.startsWith("68") || code.startsWith("50")
            || code.startsWith("51") || code.startsWith("56") || code.startsWith("58")) {
            return "sh" + code;
        }
        if (code.startsWith("00") || code.startsWith("30") || code.startsWith("15")
            || code.startsWith("16") || code.startsWith("18")) {
            return "sz" + code;
        }
        return "bj" + code;
    }

    /** 海外 (kind, code) → 腾讯 K 线代码；无对应数据返回 null */
    public static String tencentSymbol(String kind, String code) {
        switch (kind) {
            case "global":
                return MarketConfig.GLOBAL_TENCENT_CODES.get(code.toLowerCase());
            case "hk":
                return "hk" + code;
            case "us":
                String mapped = MarketConfig.GLOBAL_TENCENT_CODES.get(code.toLowerCase());
                if (mapped != null) {
                    return mapped;
                }
                return "us" + code.toUpperCase() + ".OQ";
            default:
                return null;
        }
    }

    /** 海外 K 线（腾讯 newfqkline，day/week/month）；腾讯无K线历史的标的（qqq）走纳斯达克官方 */
    public static List<Map<String, Object>> klineOverseas(String kind, String code, int datalen, String period) {
        // 腾讯 usQQQ（纳指100ETF-Invesco）几乎无K线历史（实测日K仅2根），
        // 纳指100 的K线改用纳斯达克官方 QQQ historical（口径与详情页报价一致，均为 QQQ 美元价）
        if (kind.equals("us") && NASDAQ_HISTORY_CODES.contains(code.toLowerCase())) {
            List<Map<String, Object>> daily = nasdaqDailyKline("QQQ", "etf", datalen);
            if (period.equals("week") || period.equals("month")) {
                return aggregate(daily, period);
            }
            return daily;
        }
        String sym = tencentSymbol(kind, code);
        if (sym == null) {
            return List.of();
        }
        return tencentKline(sym, datalen, period);
    }

    /** 腾讯无K线历史、需走纳斯达克官方 historical 的美股代码（详情短代码） */
    private static final Set<String> NASDAQ_HISTORY_CODES = Set.of("qqq");

    /** 纳斯达克官方日K缓存：symbol → (到期时间戳, 数据)，10 分钟 TTL，避免详情页反复拉 10 年历史 */
    private static final Map<String, Object[]> NASDAQ_DAILY_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 纳斯达克官方日K（api.nasdaq.com historical）：data.tradesTable.rows[]，
     * 行结构 {date:"MM/DD/YYYY", open/high/low/close, volume:"38,251,580"}，周K/月K由调用方聚合。
     */
    public static List<Map<String, Object>> nasdaqDailyKline(String symbol, String assetClass, int datalen) {
        String cacheKey = symbol + ":" + assetClass;
        Object[] cached = NASDAQ_DAILY_CACHE.get(cacheKey);
        if (cached != null && System.currentTimeMillis() < (long) cached[0]) {
            return (List<Map<String, Object>>) cached[1];
        }
        // 覆盖 datalen 根交易日所需日历日（约 ×2.2 含节假日/休市），限 10 年
        long days = Math.min((long) Math.ceil(datalen * 2.2) + 30, 3660);
        String from = LocalDate.now().minusDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String to = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String url = "https://api.nasdaq.com/api/quote/" + symbol + "/historical?assetclass=" + assetClass
            + "&fromdate=" + from + "&todate=" + to + "&limit=" + Math.min(datalen + 60, 9999);
        JsonNode rows;
        try {
            TrendService.HttpRequestHelper helper = new TrendService.HttpRequestHelper();
            rows = helper.getJson(url, MarketConfig.NASDAQ_HEADERS, 15)
                .path("data").path("tradesTable").path("rows");
        } catch (Exception e) {
            return List.of();
        }
        if (!rows.isArray() || rows.isEmpty()) {
            return List.of();
        }
        DateTimeFormatter mdY = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        List<Map<String, Object>> out = new ArrayList<>();
        Double prevClose = null;
        for (JsonNode r : rows) {
            try {
                String date = LocalDate.parse(r.path("date").asText().trim(), mdY)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
                double open = Double.parseDouble(r.path("open").asText().replace(",", ""));
                double high = Double.parseDouble(r.path("high").asText().replace(",", ""));
                double low = Double.parseDouble(r.path("low").asText().replace(",", ""));
                double close = Double.parseDouble(r.path("close").asText().replace(",", ""));
                long volume = MarketUtils.parseLongSafe(r.path("volume").asText("0"));
                out.add(MarketUtils.klineRow(date, open, high, low, close, volume, prevClose));
                prevClose = close;
            } catch (Exception ignored) {
                // 跳过异常行
            }
        }
        // 官方返回按日期倒序，统一为升序（与其他 K 线源一致）
        java.util.Collections.reverse(out);
        NASDAQ_DAILY_CACHE.put(cacheKey, new Object[]{
            System.currentTimeMillis() + 600_000L, java.util.Collections.unmodifiableList(out)});
        return out;
    }

    /** 腾讯 newfqkline K 线，行结构 [日期, 开盘, 收盘, 最高, 最低, 成交量] */
    public static List<Map<String, Object>> tencentKline(String sym, int datalen, String period) {
        int count;
        int startDays;
        if (period.equals("month")) {
            count = Math.min(Math.max(datalen, 60), 300);
            startDays = datalen * 31 + 90;
        } else if (period.equals("week")) {
            count = Math.min(Math.max(datalen, 100), 500);
            startDays = datalen * 7 + 45;
        } else {
            count = Math.min(Math.max(datalen, 250), 640);
            startDays = (int) (count * 2.2);
        }
        String start = LocalDate.now().minusDays(startDays).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String end = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String url = "https://proxy.finance.qq.com/ifzqgtimg/appstock/app/newfqkline/get"
            + "?param=" + sym + "," + period + "," + start + "," + end + "," + count + ",qfq";
        JsonNode resp;
        try {
            resp = MarketUtils.MAPPER.readTree(TencentClient.httpGetJson(url));
        } catch (Exception e) {
            return List.of();
        }
        JsonNode node = resp.path("data").path(sym);
        JsonNode raw = node.path(period);
        if (!raw.isArray() || raw.isEmpty()) {
            raw = node.path("qfq" + period);
        }
        if (!raw.isArray()) {
            return List.of();
        }

        List<JsonNode> rows = new ArrayList<>();
        raw.forEach(rows::add);
        int from = Math.max(0, rows.size() - datalen);

        List<Map<String, Object>> out = new ArrayList<>();
        Double prevClose = null;
        for (int i = from; i < rows.size(); i++) {
            JsonNode r = rows.get(i);
            try {
                double close = Double.parseDouble(r.get(2).asText());
                double open = Double.parseDouble(r.get(1).asText());
                double high = Double.parseDouble(r.get(3).asText());
                double low = Double.parseDouble(r.get(4).asText());
                long volume = r.size() > 5 && !r.get(5).isNull()
                    ? MarketUtils.parseLongSafe(r.get(5).asText()) : 0L;
                out.add(MarketUtils.klineRow(r.get(0).asText(), open, high, low, close, volume, prevClose));
                prevClose = close;
            } catch (Exception ignored) {
                // 跳过异常行
            }
        }
        return out;
    }

    /**
     * A股/北交所前复权日K（腾讯 fqkline，qfqday 优先回退 day），
     * 与前端图表（选股页/投资详情页）原直连 web.ifzq.gtimg.cn 同口径。
     */
    public static List<Map<String, Object>> aShareQfqDaily(String symbol, int datalen) {
        int count = Math.min(Math.max(datalen, 30), 640);
        String url = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"
            + "?param=" + symbol + ",day,,," + count + ",qfq";
        JsonNode resp;
        try {
            resp = MarketUtils.MAPPER.readTree(TencentClient.httpGetJson(url));
        } catch (Exception e) {
            return List.of();
        }
        JsonNode node = resp.path("data").path(symbol);
        JsonNode raw = node.path("qfqday");
        if (!raw.isArray() || raw.isEmpty()) {
            raw = node.path("day");
        }
        if (!raw.isArray()) {
            return List.of();
        }

        List<JsonNode> rows = new ArrayList<>();
        raw.forEach(rows::add);
        int from = Math.max(0, rows.size() - datalen);

        List<Map<String, Object>> out = new ArrayList<>();
        Double prevClose = null;
        for (int i = from; i < rows.size(); i++) {
            JsonNode r = rows.get(i);
            try {
                double close = Double.parseDouble(r.get(2).asText());
                double open = Double.parseDouble(r.get(1).asText());
                double high = Double.parseDouble(r.get(3).asText());
                double low = Double.parseDouble(r.get(4).asText());
                long volume = r.size() > 5 && !r.get(5).isNull()
                    ? MarketUtils.parseLongSafe(r.get(5).asText()) : 0L;
                out.add(MarketUtils.klineRow(r.get(0).asText(), open, high, low, close, volume, prevClose));
                prevClose = close;
            } catch (Exception ignored) {
                // 跳过异常行
            }
        }
        return out;
    }

    /** 新浪外汇日K（NewForexService.getDayKLine，JSONP 内为 `date,开,低,高,收,量|...` 管道字符串） */
    private static List<Map<String, Object>> forexDailyKline(String code) {
        String url = "https://vip.stock.finance.sina.com.cn/forex/api/jsonp.php/var%20t=/"
            + "NewForexService.getDayKLine?symbol=" + code;
        JsonNode raw = MarketUtils.parseJsonp(SinaClient.getText(url));
        if (!raw.isTextual()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        Double prevClose = null;
        for (String chunk : raw.asText().split("\\|")) {
            String[] p = chunk.trim().split(",");
            if (p.length < 5) {
                continue;
            }
            try {
                double open = Double.parseDouble(p[1]);
                double high = Double.parseDouble(p[3]);
                double low = Double.parseDouble(p[2]);
                double close = Double.parseDouble(p[4]);
                long volume = p.length > 5 && !p[5].isEmpty() ? MarketUtils.parseLongSafe(p[5]) : 0L;
                rows.add(MarketUtils.klineRow(p[0], open, high, low, close, volume, prevClose));
                prevClose = close;
            } catch (NumberFormatException ignored) {
                // 跳过异常行
            }
        }
        return rows;
    }

    /** 全球页模块 K 线（code 为完整新浪代码，按前缀分发） */
    public static List<Map<String, Object>> klineModule(String code, int datalen, String scale) {
        if (code.startsWith("sh") || code.startsWith("sz") || code.startsWith("bj")) {
            if (scale.equals("week") || scale.equals("month")) {
                return aggregate(sinaKline(code, 1000, "240"), scale);
            }
            return sinaKline(code, Math.min(datalen, 1000), scale);
        }
        if (code.startsWith("hf_") || code.startsWith("nf_")) {
            List<Map<String, Object>> daily = futuresDailyKline(code);
            if (scale.equals("week") || scale.equals("month")) {
                return aggregate(daily, scale);
            }
            return tail(daily, datalen);
        }
        if (code.startsWith("fx_")) {
            List<Map<String, Object>> daily = forexDailyKline(code);
            if (scale.equals("week") || scale.equals("month")) {
                return aggregate(daily, scale);
            }
            return tail(daily, datalen);
        }
        if (code.equals("DINI")) {
            return tencentKline("whDINIW", datalen, scale.equals("week") || scale.equals("month") ? scale : "day");
        }
        String sym = TencentClient.worldTencentSymbol(code);
        if (sym == null) {
            return List.of();
        }
        return tencentKline(sym, datalen, scale.equals("week") || scale.equals("month") ? scale : "day");
    }

    /** 新浪 K 线（scale 单位秒，240=日K；datalen 已是折算后的根数） */
    public static List<Map<String, Object>> sinaKline(String sym, int datalen, String scale) {
        String url = "https://quotes.sina.cn/cn/api/json_v2.php/CN_MarketDataService.getKLineData"
            + "?symbol=" + sym + "&scale=" + scale + "&ma=no&datalen=" + datalen;
        JsonNode raw = SinaClient.getJson(url);
        if (!raw.isArray()) {
            throw new IllegalStateException("K线接口返回异常");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        Double prevClose = null;
        for (JsonNode it : raw) {
            double close = it.path("close").asDouble();
            long volume = MarketUtils.parseLongSafe(it.path("volume").asText("0"));
            rows.add(MarketUtils.klineRow(it.path("day").asText(),
                it.path("open").asDouble(), it.path("high").asDouble(),
                it.path("low").asDouble(), close, volume, prevClose));
            prevClose = close;
        }
        return rows;
    }

    /** 日线聚合成周K/月K：日期取周期内最后一天，开=首日开，高/低合并，量求和 */
    public static List<Map<String, Object>> aggregate(List<Map<String, Object>> daily, String mode) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> cur = null;
        String curKey = null;
        Double prevClose = null;
        for (Map<String, Object> bar : daily) {
            String day = String.valueOf(bar.get("日期"));
            String k = aggregateKey(day, mode);
            if (cur == null || !k.equals(curKey)) {
                if (cur != null) {
                    rows.add(cur);
                }
                cur = new LinkedHashMap<>();
                cur.put("日期", day);
                cur.put("开盘", bar.get("开盘"));
                cur.put("最高", bar.get("最高"));
                cur.put("最低", bar.get("最低"));
                cur.put("收盘", bar.get("收盘"));
                cur.put("成交量", bar.get("成交量"));
                curKey = k;
            } else {
                cur.put("日期", day);
                cur.put("最高", Math.max(asDouble(cur.get("最高")), asDouble(bar.get("最高"))));
                cur.put("最低", Math.min(asDouble(cur.get("最低")), asDouble(bar.get("最低"))));
                cur.put("收盘", bar.get("收盘"));
                cur.put("成交量", asLong(cur.get("成交量")) + asLong(bar.get("成交量")));
            }
        }
        if (cur != null) {
            rows.add(cur);
        }
        for (Map<String, Object> r : rows) {
            double close = asDouble(r.get("收盘"));
            r.put("涨跌幅", prevClose != null && prevClose != 0
                ? Math.round((close - prevClose) / prevClose * 10000) / 100.0 : null);
            prevClose = close;
        }
        return rows;
    }

    private static String aggregateKey(String day, String mode) {
        if (mode.equals("month")) {
            return day.length() >= 7 ? day.substring(0, 7) : day;
        }
        LocalDate date = LocalDate.parse(day.substring(0, 10));
        int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = date.get(WeekFields.ISO.weekBasedYear());
        return String.format("%04d-W%02d", year, week);
    }

    /** 新浪期货日K（JSONP）：hf_ 外盘 → GlobalFuturesService；nf_ 国内 → InnerFuturesNewService */
    public static List<Map<String, Object>> futuresDailyKline(String code) {
        String url;
        String kDate;
        String kO;
        String kH;
        String kL;
        String kC;
        String kV;
        if (code.startsWith("hf_")) {
            url = "https://stock2.finance.sina.com.cn/futures/api/jsonp.php/var%20t=/"
                + "GlobalFuturesService.getGlobalFuturesDailyKLine?symbol=" + code.substring(3);
            kDate = "date";
            kO = "open";
            kH = "high";
            kL = "low";
            kC = "close";
            kV = "volume";
        } else if (code.startsWith("nf_")) {
            url = "https://stock2.finance.sina.com.cn/futures/api/jsonp.php/var%20t=/"
                + "InnerFuturesNewService.getDailyKLine?symbol=" + code.substring(3);
            kDate = "d";
            kO = "o";
            kH = "h";
            kL = "l";
            kC = "c";
            kV = "v";
        } else {
            return List.of();
        }
        JsonNode raw = MarketUtils.parseJsonp(SinaClient.getText(url));
        if (!raw.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        Double prevClose = null;
        for (JsonNode it : raw) {
            JsonNode closeNode = it.path(kC);
            Double close = MarketUtils.parseDouble(closeNode);
            Double open = MarketUtils.parseDouble(it.path(kO));
            Double high = MarketUtils.parseDouble(it.path(kH));
            Double low = MarketUtils.parseDouble(it.path(kL));
            if (close == null || open == null || high == null || low == null) {
                continue;
            }
            long volume = MarketUtils.parseLongSafe(it.path(kV).asText("0"));
            rows.add(MarketUtils.klineRow(it.path(kDate).asText(""), open, high, low, close, volume, prevClose));
            prevClose = close;
        }
        return rows;
    }

    /** 黄金模块 K 线（期货由日线聚合周/月） */
    public static List<Map<String, Object>> klineGold(String code, int datalen, String scale) {
        if (code.startsWith("sh") || code.startsWith("sz") || code.startsWith("bj")) {
            if (scale.equals("week") || scale.equals("month")) {
                return aggregate(sinaKline(code, 1000, "240"), scale);
            }
            return sinaKline(code, Math.min(datalen, 1000), scale);
        }
        List<Map<String, Object>> daily = futuresDailyKline(code);
        if (scale.equals("week") || scale.equals("month")) {
            return aggregate(daily, scale);
        }
        return tail(daily, datalen);
    }

    /** K 线序列，统一输出：日期/开盘/最高/最低/收盘/成交量/涨跌幅 */
    public static List<Map<String, Object>> kline(String kind, String code, String scale, int datalen) {
        if (kind.equals("gold")) {
            return klineGold(goldSinaSymbol(code), datalen, scale);
        }
        if (kind.equals("module")) {
            return klineModule(code, datalen, scale);
        }
        if (OVERSEAS_KINDS.contains(kind)) {
            return klineOverseas(kind, code, datalen,
                scale.equals("week") || scale.equals("month") ? scale : "day");
        }
        String sym = sinaSymbol(kind, code);
        if (scale.equals("week") || scale.equals("month")) {
            // A 股无原生周/月线：拉满 1000 根日线聚合
            return aggregate(sinaKline(sym, 1000, "240"), scale);
        }
        return sinaKline(sym, Math.min(datalen, 1000), scale);
    }

    private static List<Map<String, Object>> tail(List<Map<String, Object>> rows, int n) {
        if (rows.size() <= n) {
            return rows;
        }
        return new ArrayList<>(rows.subList(rows.size() - n, rows.size()));
    }

    private static double asDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private static long asLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0;
    }
}
