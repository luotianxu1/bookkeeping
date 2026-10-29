package com.example.finance.market;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 市场涨跌家数统计（全市场口径）（对应 investment 项目 services/activity.py）。
 *
 * <ul>
 *   <li>A股：新浪 hs_a 全量分页（60 页并发抓取）+ 涨跌停判定</li>
 *   <li>港股：港交所官方证券列表（正股代码池）+ 新浪批量行情</li>
 *   <li>美股：纳斯达克官方 screener（含 NYSE/NASDAQ 全部上市股票，自带涨跌幅）</li>
 * </ul>
 */
public final class ActivityService {

    private ActivityService() {
    }

    private static final int PAGES = 60;
    private static final int MAX_WORKERS = 12;
    private static final int HK_CHUNK = 300;

    /** 各市场缓存 TTL：A股 60s；港美统计开销大，300s */
    private static final Map<String, Long> TTL = Map.of("a", 60_000L, "hk", 300_000L, "us", 300_000L);
    private static final Map<String, MarketUtils.TtlCache<Map<String, Object>>> CACHES = Map.of(
        "a", new MarketUtils.TtlCache<>(TTL.get("a")),
        "hk", new MarketUtils.TtlCache<>(TTL.get("hk")),
        "us", new MarketUtils.TtlCache<>(TTL.get("us"))
    );
    private static final Map<String, MarketUtils.TtlCache<Map<String, Object>>> MOVERS_CACHES = Map.of(
        "hk", new MarketUtils.TtlCache<>(60_000L),
        "us", new MarketUtils.TtlCache<>(60_000L)
    );

    private static final ExecutorService POOL = Executors.newFixedThreadPool(MAX_WORKERS);

    /** 涨跌幅榜流动性门槛 */
    private static final double MIN_AMOUNT_HK = 50_000_000D;
    private static final double MIN_AMOUNT_US = 2_000_000D;
    private static final int MOVERS_TOP = 15;

    /** 11 档分布：(标签, 下界, 上界, 方向)；null 表示无穷 */
    private static final List<Object[]> BUCKETS = List.of(
        new Object[]{">10%", -10.0, null, "down"},
        new Object[]{"10~7%", -10.0, -7.0, "down"},
        new Object[]{"7~5%", -7.0, -5.0, "down"},
        new Object[]{"5~3%", -5.0, -3.0, "down"},
        new Object[]{"3~0%", -3.0, 0.0, "down"},
        new Object[]{"0%", 0.0, 0.0, "flat"},
        new Object[]{"0~3%", 0.0, 3.0, "up"},
        new Object[]{"3~5%", 3.0, 5.0, "up"},
        new Object[]{"5~7%", 5.0, 7.0, "up"},
        new Object[]{"7~10%", 7.0, 10.0, "up"},
        new Object[]{">10%", 10.0, null, "up"}
    );

    /** 纳斯达克 screener sector → 中文板块名 */
    private static final Map<String, String> US_SECTOR_ZH = Map.ofEntries(
        Map.entry("Technology", "信息技术"),
        Map.entry("Health Care", "医疗保健"),
        Map.entry("Finance", "金融"),
        Map.entry("Consumer Discretionary", "可选消费"),
        Map.entry("Industrials", "工业"),
        Map.entry("Energy", "能源"),
        Map.entry("Basic Materials", "基础材料"),
        Map.entry("Consumer Staples", "必需消费"),
        Map.entry("Utilities", "公用事业"),
        Map.entry("Real Estate", "房地产"),
        Map.entry("Communication Services", "通信服务"),
        Map.entry("Transportation", "运输"),
        Map.entry("Miscellaneous", "综合")
    );

    /** 涨跌幅 → 档位下标；不属于任何档返回 -1 */
    private static int bucketOf(double pct) {
        if (pct == 0) {
            return 5;
        }
        if (pct < 0) {
            if (pct <= -10) {
                return 0;
            }
            // -10 < pct < 0：只落在 1~4 档（第 0 档上界为 null，不能参与比较）
            for (int i = 1; i <= 4; i++) {
                Object[] b = BUCKETS.get(i);
                if (lo(b) < pct && pct <= hi(b)) {
                    return i;
                }
            }
            return -1;
        }
        if (pct >= 10) {
            return 10;
        }
        // 0 < pct < 10：只落在 6~9 档（第 10 档上界为 null）
        for (int i = 6; i < BUCKETS.size() - 1; i++) {
            Object[] b = BUCKETS.get(i);
            if (lo(b) < pct && pct <= hi(b)) {
                return i;
            }
        }
        return -1;
    }

    private static double lo(Object[] bucket) {
        return (Double) bucket[1];
    }

    private static Double hi(Object[] bucket) {
        return (Double) bucket[2];
    }

    /** 涨跌幅列表 → 涨跌家数 + 11 档分布 */
    private static Map<String, Object> aggregate(List<Double> percents) {
        int up = 0;
        int down = 0;
        int flat = 0;
        int[] counts = new int[BUCKETS.size()];
        for (Double c : percents) {
            if (c > 0) {
                up++;
            } else if (c < 0) {
                down++;
            } else {
                flat++;
            }
            int bi = bucketOf(c);
            if (bi >= 0) {
                counts[bi]++;
            }
        }
        List<Map<String, Object>> dist = new ArrayList<>();
        for (int i = 0; i < BUCKETS.size(); i++) {
            Object[] b = BUCKETS.get(i);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("label", b[0]);
            d.put("count", counts[i]);
            d.put("side", b[3]);
            dist.add(d);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("up", up);
        out.put("down", down);
        out.put("flat", flat);
        out.put("dist", dist);
        out.put("sample", percents.size());
        return out;
    }

    // ---------- A 股涨跌停判定 ----------
    // 涨跌幅限制：主板 10%、创业板/科创板 20%、北交所 30%、ST 5%
    private static double limitRatio(String symbol, String name) {
        String code = symbol.length() >= 6 ? symbol.substring(symbol.length() - 6) : symbol;
        if (name.toUpperCase().contains("ST")) {
            return 0.05;
        }
        if (code.startsWith("300") || code.startsWith("301") || code.startsWith("302")
            || code.startsWith("688") || code.startsWith("689")) {
            return 0.20;
        }
        if (code.startsWith("43") || code.startsWith("83") || code.startsWith("87")
            || code.startsWith("88") || code.startsWith("92")) {
            return 0.30;
        }
        return 0.10;
    }

    /** 现价是否封住涨停/跌停价；停牌（无价）返回 null */
    private static String limitState(String symbol, String name, double trade, double settle) {
        if (trade <= 0 || settle <= 0) {
            return null;
        }
        double r = limitRatio(symbol, name);
        double raw = trade / settle - 1;
        // 新股上市初期无涨跌幅限制：涨跌幅远超限制比例（>1.5 倍）不计入涨跌停
        if (Math.abs(raw) > r * 1.5) {
            return null;
        }
        double upPx = Math.round(settle * (1 + r) * 100) / 100.0;
        double dnPx = Math.round(settle * (1 - r) * 100) / 100.0;
        if (trade >= upPx - 0.001) {
            return "up";
        }
        if (trade <= dnPx + 0.001) {
            return "down";
        }
        return null;
    }

    private static JsonNode fetchPage(int page) {
        String url = "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/"
            + "Market_Center.getHQNodeData?page=" + page + "&num=100&sort=symbol&asc=1&node=hs_a";
        return SinaClient.getJson(url);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> countA() {
        List<Future<JsonNode>> futures = new ArrayList<>();
        for (int page = 1; page <= PAGES; page++) {
            final int p = page;
            futures.add(POOL.submit(() -> fetchPage(p)));
        }
        List<Double> percents = new ArrayList<>();
        int[] limitUp = {0};
        int[] limitDown = {0};
        Map<String, List<Map<String, Object>>> detail = Map.of("up", new ArrayList<>(), "down", new ArrayList<>());
        for (Future<JsonNode> future : futures) {
            JsonNode rows;
            try {
                rows = future.get();
            } catch (Exception e) {
                break;
            }
            if (!rows.isArray()) {
                break;
            }
            for (JsonNode it : rows) {
                Double c = MarketUtils.parseDouble(it.path("changepercent").asText(""));
                if (c != null) {
                    percents.add(c);
                }
                double trade = orZero(MarketUtils.parseDouble(it.path("trade").asText("")));
                double settle = orZero(MarketUtils.parseDouble(it.path("settlement").asText("")));
                String symbol = it.path("symbol").asText("");
                String state = limitState(symbol, it.path("name").asText(""), trade, settle);
                if ("up".equals(state) || "down".equals(state)) {
                    if (state.equals("up")) {
                        limitUp[0]++;
                    } else {
                        limitDown[0]++;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("symbol", symbol);
                    row.put("code", symbol.length() >= 6 ? symbol.substring(symbol.length() - 6) : symbol);
                    row.put("name", it.path("name").asText(""));
                    row.put("price", trade);
                    row.put("pct", c);
                    row.put("amount", orZero(MarketUtils.parseDouble(it.path("amount").asText(""))));
                    detail.get(state).add(row);
                }
            }
        }
        // 明细按成交额降序（封板热度）
        ((List<Map<String, Object>>) detail.get("up")).sort(amountDesc());
        ((List<Map<String, Object>>) detail.get("down")).sort(amountDesc());
        Map<String, Object> result = aggregate(percents);
        result.put("limit_up", limitUp[0]);
        result.put("limit_down", limitDown[0]);
        result.put("_limit_detail", detail);
        return result;
    }

    private static Comparator<Map<String, Object>> amountDesc() {
        return (a, b) -> Double.compare(asDouble(b.get("amount")), asDouble(a.get("amount")));
    }

    /** 港交所正股代码池 → 新浪批量行情 → 涨跌分布 */
    private static Map<String, Object> countHk() {
        List<String> codes = HkexService.equityCodes();
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < codes.size(); i += HK_CHUNK) {
            List<String> chunk = new ArrayList<>();
            for (int j = i; j < Math.min(i + HK_CHUNK, codes.size()); j++) {
                chunk.add("rt_hk" + codes.get(j));
            }
            chunks.add(chunk);
        }
        List<Future<Map<String, List<String>>>> futures = new ArrayList<>();
        for (List<String> chunk : chunks) {
            futures.add(POOL.submit(() -> SinaClient.batchQuotes(chunk)));
        }
        Map<String, List<String>> fields = new HashMap<>();
        for (Future<Map<String, List<String>>> future : futures) {
            try {
                fields.putAll(future.get());
            } catch (Exception ignored) {
                // 单批失败不阻断统计
            }
        }
        List<Double> percents = new ArrayList<>();
        List<Map<String, Object>> detail = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : fields.entrySet()) {
            SinaClient.QuoteRow row = SinaClient.quoteRow(entry.getKey(), entry.getValue(), entry.getKey());
            Double pct = row == null ? null : (Double) row.get("涨跌幅");
            if (pct != null) {
                percents.add(pct);
                if (row != null && row.get("成交额") != null) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("code", row.get("代码"));
                    d.put("name", row.get("名称"));
                    d.put("price", row.get("最新价") != null ? row.get("最新价") : 0);
                    d.put("pct", pct);
                    d.put("amount", row.get("成交额"));
                    detail.add(d);
                }
            }
        }
        Map<String, Object> result = aggregate(percents);
        result.put("_detail", detail);
        return result;
    }

    /** 纳斯达克官方 screener 全量行 → 涨跌分布 */
    private static Map<String, Object> countUs() {
        JsonNode root = fetchNasdaqScreener();
        JsonNode rows = root.path("data").path("rows");
        List<Double> percents = new ArrayList<>();
        List<Map<String, Object>> detail = new ArrayList<>();
        if (rows.isArray()) {
            for (JsonNode it : rows) {
                Double c = MarketUtils.parseDouble(it.path("pctchange").asText("").replace("%", ""));
                if (c != null) {
                    percents.add(c);
                }
                Double last = MarketUtils.parseDouble(it.path("lastsale").asText("").replace("$", ""));
                double vol = orZero(MarketUtils.parseDouble(it.path("volume").asText("")));
                if (last != null && last != 0 && vol > 0) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("code", it.path("symbol").asText("").toUpperCase());
                    d.put("name", it.path("name").asText(""));
                    d.put("price", last);
                    d.put("pct", c);
                    d.put("amount", last * vol); // 约合成交额（美元）
                    d.put("sector", it.path("sector").asText("").isEmpty() ? "其他" : it.path("sector").asText());
                    detail.add(d);
                }
            }
        }
        Map<String, Object> result = aggregate(percents);
        result.put("_detail", detail);
        return result;
    }

    /** 拉取纳斯达克官方全市场 screener（activity 与 turnover 共用） */
    static JsonNode fetchNasdaqScreener() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(MarketConfig.NASDAQ_SCREENER_URL))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> resp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .build().send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("纳斯达克 screener 返回 " + resp.statusCode());
            }
            return MarketUtils.MAPPER.readTree(resp.body());
        } catch (Exception e) {
            throw new IllegalStateException("纳斯达克 screener 请求失败: " + e.getMessage(), e);
        }
    }

    /** 市场概况：涨跌家数 + 11 档分布（全市场口径，按市场独立缓存） */
    public static Map<String, Object> marketActivity(String market) {
        Map<String, Object> raw = rawResult(market);
        // 剥离内部明细键（_limit_detail/_detail，由 limits 与 movers 端点单独提供）
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (!entry.getKey().startsWith("_")) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    /** A 股涨跌停个股明细（复用 activity 缓存）：up/down 各按成交额降序 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> marketLimits() {
        Map<String, Object> raw = rawResult("a");
        Map<String, List<Map<String, Object>>> detail =
            (Map<String, List<Map<String, Object>>>) raw.getOrDefault("_limit_detail", Map.of());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("limit_up", raw.getOrDefault("limit_up", 0));
        out.put("limit_down", raw.getOrDefault("limit_down", 0));
        out.put("up", detail.getOrDefault("up", List.of()));
        out.put("down", detail.getOrDefault("down", List.of()));
        return out;
    }

    /** 港股/美股涨跌幅榜（复用 activity 缓存）：领涨/领跌 TOP15，美股补中文名 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> marketMovers(String market) {
        if (!market.equals("hk") && !market.equals("us")) {
            throw new IllegalArgumentException("未知市场: " + market);
        }
        Map<String, Object> cached = MOVERS_CACHES.get(market).get(() -> buildMovers(market));
        return cached;
    }

    private static Map<String, Object> buildMovers(String market) {
        Map<String, Object> raw = rawResult(market);
        double minAmount = market.equals("hk") ? MIN_AMOUNT_HK : MIN_AMOUNT_US;
        List<Map<String, Object>> detail = new ArrayList<>();
        for (Map<String, Object> row : (List<Map<String, Object>>) raw.getOrDefault("_detail", List.of())) {
            Double pct = (Double) row.get("pct");
            double amount = asDouble(row.get("amount"));
            if (pct != null && pct != 0 && amount >= minAmount) {
                detail.add(new LinkedHashMap<>(row));
            }
        }
        detail.sort((a, b) -> Double.compare(asDouble(b.get("pct")), asDouble(a.get("pct"))));
        List<Map<String, Object>> up = new ArrayList<>(detail.subList(0, Math.min(MOVERS_TOP, detail.size())));
        List<Map<String, Object>> down = new ArrayList<>();
        for (int i = detail.size() - 1; i >= 0 && down.size() < MOVERS_TOP; i--) {
            down.add(detail.get(i));
        }
        if (market.equals("us")) {
            List<String> symbols = new ArrayList<>();
            up.forEach(r -> symbols.add((String) r.get("code")));
            down.forEach(r -> symbols.add((String) r.get("code")));
            Map<String, String> names = tencentUsNames(symbols);
            for (Map<String, Object> row : up) {
                row.put("name", names.getOrDefault((String) row.get("code"),
                    cleanUsName((String) row.get("name"))));
            }
            for (Map<String, Object> row : down) {
                row.put("name", names.getOrDefault((String) row.get("code"),
                    cleanUsName((String) row.get("name"))));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("up", up);
        result.put("down", down);
        return result;
    }

    /** 精简美股英文名：去掉 Common Stock / Inc. / Corp. 等通用后缀 */
    static String cleanUsName(String name) {
        String cleaned = name;
        cleaned = cleaned.replaceFirst(
            "(?i)\\s*(Common Stock|Ordinary Shares?|ADS|ADR|Class [A-Z]+|Warrants?|Units?|Rights?)\\b.*$", "");
        cleaned = cleaned.replaceFirst(
            "(?i)\\s*(Inc\\.?|Corp\\.?|Corporation|Limited|Ltd\\.?|Co\\.?,?|PLC|Company)\\s*$", "");
        cleaned = cleaned.replaceAll("^[\\s,.-]+|[\\s,.-]+$", "");
        return cleaned.isEmpty() ? name : cleaned;
    }

    /** 腾讯批量美股行情取中文名（仅热门股有，冷门股自动保留英文名） */
    private static Map<String, String> tencentUsNames(List<String> symbols) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        try {
            List<String> codes = new ArrayList<>();
            for (String s : symbols) {
                codes.add("us" + s);
            }
            String text = TencentClient.httpGetText("https://qt.gtimg.cn/q=" + String.join(",", codes), 10);
            Map<String, String> out = new HashMap<>();
            Matcher m = Pattern.compile("v_us(\\w+)=\"([^\"]*)\"").matcher(text);
            while (m.find()) {
                String[] parts = m.group(2).split("~", -1);
                String cn = parts.length > 1 ? parts[1].trim() : "";
                if (!cn.isEmpty() && cn.chars().anyMatch(c -> c >= 0x4e00 && c <= 0x9fff)) {
                    out.put(m.group(1), cn);
                }
            }
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 美股行业板块聚合（纳斯达克 screener sector 字段）：平均涨跌幅 + 家数 + 合计成交额 */
    public static List<Map<String, Object>> usSectors() {
        Map<String, Object> raw = rawResult("us");
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : (List<Map<String, Object>>) raw.getOrDefault("_detail", List.of())) {
            groups.computeIfAbsent(String.valueOf(row.get("sector")), k -> new ArrayList<>()).add(row);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
            List<Map<String, Object>> rows = entry.getValue();
            String name = US_SECTOR_ZH.getOrDefault(entry.getKey(), entry.getKey());
            double pctSum = 0;
            int pctCount = 0;
            double amountSum = 0;
            for (Map<String, Object> r : rows) {
                Double pct = (Double) r.get("pct");
                if (pct != null) {
                    pctSum += pct;
                    pctCount++;
                }
                amountSum += asDouble(r.get("amount"));
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("代码", name);
            item.put("名称", name);
            item.put("公司家数", rows.size());
            item.put("平均价", null);
            item.put("涨跌幅", pctCount > 0 ? Math.round(pctSum / pctCount * 1000) / 1000.0 : null);
            item.put("总成交额", amountSum);
            item.put("领涨股", "");
            item.put("领涨股涨跌幅", null);
            out.add(item);
        }
        out.sort((a, b) -> Double.compare(asDouble(b.get("涨跌幅")), asDouble(a.get("涨跌幅"))));
        return out;
    }

    /** 取缓存原始结果（含 _limit_detail/_detail），无则计算 */
    private static Map<String, Object> rawResult(String market) {
        MarketUtils.TtlCache<Map<String, Object>> cache = CACHES.get(market);
        if (cache == null) {
            throw new IllegalArgumentException("未知市场: " + market);
        }
        return cache.get(() -> switch (market) {
            case "a" -> countA();
            case "hk" -> countHk();
            case "us" -> countUs();
            default -> throw new IllegalArgumentException("未知市场: " + market);
        });
    }

    private static double orZero(Double v) {
        return v == null ? 0 : v;
    }

    private static double asDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }
}
