package com.example.market.market;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 分时（当日 1 分钟）数据（对应 investment 项目 services/trend.py）。
 *
 * <ul>
 *   <li>A 股：新浪 getMinlineData，自带均价线 avg_p</li>
 *   <li>海外（港股/全球指数）：腾讯 minute/query，均价由累计成交额/累计成交量推算</li>
 *   <li>美股：纳斯达克官方 chart API；道指/标普500 用 ETF（SPY/DIA）分时按昨收比折算</li>
 *   <li>期货（黄金/全球页 hf_/nf_）：新浪 1 分钟线，会话切分按交易日</li>
 *   <li>5 日分时回看：新浪 5 分钟K线（VWAP 均价线），按交易日分组</li>
 * </ul>
 */
public final class TrendService {

    private TrendService() {
    }

    private static final ZoneId ET = ZoneId.of("America/New_York");

    /** 纳斯达克官方 chart API：仅覆盖自家指数，道指/标普500 无分钟序列 */
    private static final Map<String, String[]> NASDAQ_CHART_INDEX = Map.of(
        "nasdaq", new String[]{"COMP", "index"}
    );

    /** 标普500/道指无公开指数分钟源，用对应 ETF（SPY/DIA）分时按昨收比折算回指数点位 */
    private static final Map<String, String[]> US_ETF_PROXY = Map.of(
        "sp500", new String[]{"SPY", "usINX", "标普500"},
        "dji", new String[]{"DIA", "usDJI", "道琼斯"}
    );

    private static final Map<String, String[]> MODULE_NASDAQ_CHART = Map.of(
        "int_nasdaq", new String[]{"COMP", "index"}
    );
    private static final Map<String, String[]> MODULE_ETF_PROXY = Map.of(
        "int_sp500", new String[]{"SPY", "usINX", "标普500"},
        "int_dji", new String[]{"DIA", "usDJI", "道琼斯"}
    );

    /** 分时点：{time, price, avg, volume} */
    public static Map<String, Object> trendPoint(String time, double price, Double avg, long volume) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("time", time);
        p.put("price", price);
        p.put("avg", avg);
        p.put("volume", volume);
        return p;
    }

    public static Map<String, Object> trendPayload(String symbol, String name, String date,
                                                   Double prevClose, List<Map<String, Object>> points) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("symbol", symbol);
        out.put("name", name);
        out.put("date", date);
        out.put("prev_close", prevClose);
        out.put("points", points);
        return out;
    }

    /** A股分时：新浪 minline，1 分钟一根，昨收取实时行情的昨收字段 */
    public static Map<String, Object> trendSina(String kind, String code) {
        String sym = KlineService.sinaSymbol(kind, code);
        String url = "https://cn.finance.sina.com.cn/minline/getMinlineData?symbol=" + sym + "&page=1&num=242";
        JsonNode resp = SinaClient.getJson(url);
        JsonNode data = resp.path("result").path("data");

        List<Map<String, Object>> points = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode it : data) {
                try {
                    String m = it.path("m").asText();
                    String time = m.length() > 5 ? m.substring(0, 5) : m;
                    double price = it.path("p").asDouble();
                    Double avg = it.hasNonNull("avg_p") && it.path("avg_p").asDouble() != 0
                        ? it.path("avg_p").asDouble() : null;
                    long volume = MarketUtils.parseLongSafe(it.path("v").asText("0"));
                    points.add(trendPoint(time, price, avg, volume));
                } catch (Exception ignored) {
                    // 跳过异常点
                }
            }
        }

        List<String> fields = SinaClient.batchQuotes(List.of(sym)).get(sym);
        Double prev = null;
        String name = code;
        if (fields != null) {
            if (fields.size() > 2 && !fields.get(2).isEmpty()) {
                prev = MarketUtils.parseDouble(fields.get(2));
            }
            if (!fields.isEmpty() && !fields.get(0).isEmpty()) {
                name = fields.get(0);
            }
        }
        return trendPayload(sym, name, "", prev, points);
    }

    /** 海外分时：腾讯 minute/query，行结构 `HHMM 价格 累计量 累计额` */
    public static Map<String, Object> trendTencent(String kind, String code) {
        String sym = KlineService.tencentSymbol(kind, code);
        if (sym == null) {
            return trendPayload("", code, "", null, List.of());
        }
        return tencentMinute(sym, code);
    }

    /** 腾讯分时通用实现（sym 为腾讯代码，如 hkHSI / usDJI） */
    public static Map<String, Object> tencentMinute(String sym, String fallbackName) {
        String url = "https://web.ifzq.gtimg.cn/appstock/app/minute/query?code=" + sym;
        JsonNode resp;
        try {
            resp = MarketUtils.MAPPER.readTree(TencentClient.httpGetJson(url));
        } catch (Exception e) {
            return trendPayload(sym, fallbackName, "", null, List.of());
        }
        JsonNode node = resp.path("data").path(sym);
        JsonNode inner = node.path("data");
        JsonNode raw = inner.path("data");
        JsonNode qt = node.path("qt").path(sym);

        Double prev = null;
        if (qt.isArray() && qt.size() > 4 && !qt.get(4).asText().isEmpty()) {
            prev = MarketUtils.parseDouble(qt.get(4));
        }

        List<Map<String, Object>> points = new ArrayList<>();
        double prevVol = 0;
        if (raw.isArray()) {
            for (JsonNode r : raw) {
                String[] parts = r.asText().trim().split("\\s+");
                if (parts.length < 3) {
                    continue;
                }
                try {
                    double price = Double.parseDouble(parts[1]);
                    double vol = Double.parseDouble(parts[2]);
                    double amt = parts.length > 3 ? Double.parseDouble(parts[3]) : 0;
                    Double avg = null;
                    if (vol > 0 && amt > 0) {
                        double cand = amt / vol;
                        // 均价与现价偏差过大（指数等量纲不同）视为无效
                        if (price * 0.5 <= cand && cand <= price * 2) {
                            avg = Math.round(cand * 1000) / 1000.0;
                        }
                    }
                    String t = parts[0];
                    String time = t.substring(0, 2) + ":" + t.substring(2);
                    points.add(trendPoint(time, price, avg, (long) Math.max(0, vol - prevVol)));
                    prevVol = vol;
                } catch (NumberFormatException ignored) {
                    // 跳过异常行
                }
            }
        }

        String name = fallbackName;
        if (qt.isArray() && qt.size() > 1 && !qt.get(1).asText().isEmpty()) {
            name = qt.get(1).asText();
        }
        String date = inner.path("date").asText("");
        if (date.length() == 8) {
            date = date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6);
        }
        // 交易时段外腾讯只返回 1 根收盘汇总行（全日累计量），不算分时数据
        if (points.size() <= 1) {
            return trendPayload(sym, name, date, prev, List.of());
        }
        return trendPayload(sym, name, date, prev, points);
    }

    /** 分时序列，统一输出：points[{time, price, avg, volume}] + 昨收基准 */
    public static Map<String, Object> trend(String kind, String code) {
        if (kind.equals("module")) {
            return trendModule(code);
        }
        if (kind.equals("gold")) {
            return trendGold(code);
        }
        if (kind.equals("us")) {
            return trendUs(code);
        }
        if (KlineService.OVERSEAS_KINDS.contains(kind)) {
            return trendTencent(kind, code);
        }
        return trendSina(kind, code);
    }

    /** 美股详情分时：个股/纳指综合走纳斯达克官方 chart，道指/标普500 用 ETF 折算 */
    public static Map<String, Object> trendUs(String code) {
        String c = code.toLowerCase();
        if (NASDAQ_CHART_INDEX.containsKey(c)) {
            String[] def = NASDAQ_CHART_INDEX.get(c);
            return nasdaqMinute(def[0], def[1], code);
        }
        if (US_ETF_PROXY.containsKey(c)) {
            String[] def = US_ETF_PROXY.get(c);
            return etfProxyMinute(def[0], def[1], def[2]);
        }
        return nasdaqMinute(code.toUpperCase(), "stocks", code);
    }

    /** ETF 分时代理：SPY/DIA 序列 × (指数昨收/ETF昨收) 折算回指数点位 */
    private static Map<String, Object> etfProxyMinute(String etfSym, String indexSym, String name) {
        Map<String, Object> etf = nasdaqMinute(etfSym, "etf", name);
        List<Map<String, Object>> etfPoints = asPoints(etf.get("points"));
        Double etfPrev = (Double) etf.get("prev_close");
        if (etfPoints.isEmpty() || etfPrev == null) {
            return trendPayload(indexSym, name, "", null, List.of());
        }
        List<String> tf = TencentClient.tencentQt(List.of(indexSym)).get(indexSym);
        Double idxPrev = null;
        if (tf != null && tf.size() > 4 && !tf.get(4).isEmpty()) {
            idxPrev = MarketUtils.parseDouble(tf.get(4));
        }
        if (idxPrev == null) {
            return trendPayload(indexSym, name, "", null, List.of());
        }
        double ratio = idxPrev / etfPrev;
        List<Map<String, Object>> points = new ArrayList<>();
        for (Map<String, Object> p : etfPoints) {
            double price = asDouble(p.get("price")) * ratio;
            points.add(trendPoint(String.valueOf(p.get("time")),
                Math.round(price * 100) / 100.0, null, asLong(p.get("volume"))));
        }
        return trendPayload(indexSym, name, String.valueOf(etf.get("date")), idxPrev, points);
    }

    /** 纳斯达克官方分时：data.chart[] = {z:{dateTime:'10:25 AM ET'}, y}，含盘前 */
    public static Map<String, Object> nasdaqMinute(String sym, String assetClass, String fallbackName) {
        Map<String, Object> empty = trendPayload(sym, fallbackName, "", null, List.of());
        String url = "https://api.nasdaq.com/api/quote/" + sym + "/chart?assetclass=" + assetClass;
        JsonNode data;
        try {
            HttpRequestHelper helper = new HttpRequestHelper();
            JsonNode root = helper.getJson(url, MarketConfig.NASDAQ_HEADERS, 15);
            data = root.path("data");
        } catch (Exception e) {
            return empty;
        }
        JsonNode chart = data.path("chart");
        if (!chart.isArray() || chart.isEmpty()) {
            return empty;
        }

        // timeAsOf: "Oct 28, 2026 10:25 AM ET" → 日期
        String day = "";
        Matcher m = Pattern.compile("[A-Z][a-z]{2} \\d{1,2}, \\d{4}")
            .matcher(String.valueOf(data.path("timeAsOf").asText("")));
        if (m.find()) {
            try {
                day = LocalDate.parse(m.group(),
                    DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH))
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception ignored) {
                // 日期解析失败保持空
            }
        }
        // previousClose: "$347.10" → 347.10
        Double prev = null;
        String pc = String.valueOf(data.path("previousClose").asText(""))
            .replace("$", "").replace(",", "").trim();
        if (!pc.isEmpty()) {
            prev = MarketUtils.parseDouble(pc);
        }

        List<Map<String, Object>> points = new ArrayList<>();
        for (JsonNode p : chart) {
            String t = p.path("z").path("dateTime").asText("").toUpperCase();
            try {
                String[] seg = t.split(" ");
                String clock = seg[0];
                String ampm = seg[1];
                String[] hm = clock.split(":");
                int hh = Integer.parseInt(hm[0]) % 12 + ("PM".equals(ampm) ? 12 : 0);
                points.add(trendPoint(String.format("%02d:%s", hh, hm[1]),
                    p.path("y").asDouble(), null, 0L));
            } catch (Exception ignored) {
                // 跳过异常点
            }
        }
        if (points.isEmpty()) {
            return empty;
        }
        String name = data.path("company").asText(fallbackName);
        return trendPayload(sym, name, day, prev, points);
    }

    /** 全球页模块分时（code 为完整新浪代码，按前缀分发） */
    public static Map<String, Object> trendModule(String code) {
        if (MODULE_NASDAQ_CHART.containsKey(code)) {
            String[] def = MODULE_NASDAQ_CHART.get(code);
            return nasdaqMinute(def[0], def[1], code);
        }
        if (MODULE_ETF_PROXY.containsKey(code)) {
            String[] def = MODULE_ETF_PROXY.get(code);
            return etfProxyMinute(def[0], def[1], def[2]);
        }
        if (code.startsWith("rt_hk")) {
            return tencentMinute("hk" + code.substring(5), code);
        }
        if (code.startsWith("nf_")) {
            return futuresMinute(code, true);
        }
        if (code.startsWith("hf_")) {
            return futuresMinute(code, false);
        }
        return trendPayload(code, code, "", null, List.of());
    }

    /** 黄金详情分时（code 为路由短代码）：期货走新浪 1 分钟线，ETF 走新浪 A 股分时 */
    public static Map<String, Object> trendGold(String code) {
        String full = KlineService.goldSinaSymbol(code);
        if (full.startsWith("nf_")) {
            return futuresMinute(full, true);
        }
        if (full.startsWith("hf_")) {
            return futuresMinute(full, false);
        }
        return trendSina("stock", full.substring(2));
    }

    /** 新浪期货 1 分钟分时（内盘 getFewMinLine / 外盘 GlobalService.getMinK），会话按交易日切分 */
    public static Map<String, Object> futuresMinute(String sym, boolean inner) {
        String base = sym.substring(3); // nf_AU0 → AU0 / hf_XAU → XAU
        String url;
        if (inner) {
            url = "https://stock2.finance.sina.com.cn/futures/api/jsonp.php/var%20_=/"
                + "InnerFuturesNewService.getFewMinLine?symbol=" + base + "&type=1";
        } else {
            url = "https://gu.sina.cn/ft/api/jsonp.php/var%20t=/GlobalService.getMinK?symbol=" + base + "&type=1";
        }
        JsonNode bars;
        try {
            String text = SinaClient.getText(url, java.nio.charset.StandardCharsets.UTF_8, 10);
            JsonNode payload = MarketUtils.parseJsonp(text);
            bars = payload.isArray() ? payload : null;
        } catch (Exception e) {
            bars = null;
        }
        if (bars == null) {
            return trendPayload(sym, sym, "", null, List.of());
        }

        // 解析为 [date, time, close, volume] 行
        List<String[]> parsed = new ArrayList<>();
        List<Double> closes = new ArrayList<>();
        List<Long> volumes = new ArrayList<>();
        for (JsonNode b : bars) {
            String d = b.path("d").asText("");
            try {
                parsed.add(new String[]{d.length() >= 10 ? d.substring(0, 10) : d,
                    d.length() >= 16 ? d.substring(11, 16) : "", ""});
                closes.add(b.path("c").asDouble());
                volumes.add(MarketUtils.parseLongSafe(b.path("v").asText("0")));
            } catch (Exception ignored) {
                // 跳过异常行
            }
        }
        if (parsed.isEmpty()) {
            return trendPayload(sym, sym, "", null, List.of());
        }

        String lastDate = parsed.get(parsed.size() - 1)[0];
        String lastTime = parsed.get(parsed.size() - 1)[1];

        // 会话起点下标（parsed 后缀）
        int startIdx;
        if (inner) {
            if (lastTime.compareTo("20:00") >= 0) {
                // 夜盘进行中：会话 = 昨日 20:00 后 + 当前交易日（last_date+1）凌晨延续
                String tradeDay = LocalDate.parse(lastDate).plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
                startIdx = sessionStart(parsed, i -> (parsed.get(i)[0].equals(lastDate)
                    && parsed.get(i)[1].compareTo("20:00") >= 0)
                    || (parsed.get(i)[0].equals(tradeDay) && parsed.get(i)[1].compareTo("06:00") < 0));
            } else {
                // 日盘/凌晨时段：交易日 = last_date，会话 = 前夜夜盘 + 当日全天
                String prevDay = LocalDate.parse(lastDate).minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
                startIdx = sessionStart(parsed, i -> (parsed.get(i)[0].equals(prevDay)
                    && parsed.get(i)[1].compareTo("20:00") >= 0)
                    || (parsed.get(i)[0].equals(lastDate)
                    && (parsed.get(i)[1].compareTo("06:00") < 0 || parsed.get(i)[1].compareTo("09:00") >= 0)));
            }
        } else {
            boolean allBefore6 = true;
            for (String[] p : parsed) {
                if (p[0].equals(lastDate) && p[1].compareTo("06:00") >= 0) {
                    allBefore6 = false;
                    break;
                }
            }
            if (allBefore6 && parsed.stream().anyMatch(p -> p[0].equals(lastDate))) {
                // 凌晨收盘阶段：会话从昨日 06:00 开始，延续至今日凌晨
                String prevDay = LocalDate.parse(lastDate).minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
                startIdx = sessionStart(parsed, i -> (parsed.get(i)[0].equals(prevDay)
                    && parsed.get(i)[1].compareTo("06:00") >= 0)
                    || parsed.get(i)[0].equals(lastDate));
            } else {
                startIdx = sessionStart(parsed, i -> parsed.get(i)[0].equals(lastDate)
                    && parsed.get(i)[1].compareTo("06:00") >= 0);
            }
        }

        // 会话恒为 parsed 的后缀，昨收取会话前最后一根的收盘
        Double prev = startIdx > 0 ? closes.get(startIdx - 1) : null;
        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = startIdx; i < parsed.size(); i++) {
            points.add(trendPoint(parsed.get(i)[1], closes.get(i), null, volumes.get(i)));
        }
        return trendPayload(sym, sym, lastDate, prev, points);
    }

    /** 找到满足条件的最早连续后缀起点 */
    private static int sessionStart(List<String[]> parsed, java.util.function.IntPredicate inSession) {
        int start = parsed.size();
        for (int i = 0; i < parsed.size(); i++) {
            if (inSession.test(i)) {
                start = i;
                break;
            }
        }
        return start;
    }

    /** 近 N 个交易日分时回看（新浪 5 分钟K线，按日分组，VWAP 均价线）。仅 A 股。 */
    public static Map<String, Object> trendHistory(String kind, String code, int days) {
        if (KlineService.OVERSEAS_KINDS.contains(kind)) {
            return null;
        }
        String sym = KlineService.sinaSymbol(kind, code);
        int datalen = 48 * (days + 1) + 20;
        String url = "https://quotes.sina.cn/cn/api/jsonp_v2.php/var%20_f=/CN_MarketDataService.getKLineData"
            + "?symbol=" + sym + "&scale=5&ma=no&datalen=" + datalen;
        JsonNode bars;
        try {
            JsonNode payload = MarketUtils.parseJsonp(SinaClient.getText(url, java.nio.charset.StandardCharsets.UTF_8, 10));
            bars = payload.isArray() ? payload : null;
        } catch (Exception e) {
            bars = null;
        }
        if (bars == null) {
            return null;
        }

        // 按交易日分组（保持时间升序）
        Map<String, List<double[]>> groups = new LinkedHashMap<>();
        for (JsonNode b : bars) {
            try {
                String day = b.path("day").asText();
                if (day.length() < 16) {
                    continue;
                }
                groups.computeIfAbsent(day.substring(0, 10), k -> new ArrayList<>())
                    .add(new double[]{
                        Double.parseDouble(day.substring(11, 16).replace(":", "")),
                        b.path("close").asDouble(),
                        b.path("volume").asDouble(0),
                        b.path("amount").asDouble(0)
                    });
            } catch (Exception ignored) {
                // 跳过异常行
            }
        }
        if (groups.isEmpty()) {
            return null;
        }

        List<String> allDays = new ArrayList<>(groups.keySet());
        List<Map<String, Object>> built = new ArrayList<>();
        for (int i = 1; i < allDays.size(); i++) {
            String day = allDays.get(i);
            List<double[]> ptsRaw = groups.get(day);
            double prevClose = groups.get(allDays.get(i - 1)).get(groups.get(allDays.get(i - 1)).size() - 1)[1];
            List<Map<String, Object>> points = new ArrayList<>();
            double cumV = 0;
            double cumA = 0;
            for (double[] p : ptsRaw) {
                cumV += p[2];
                cumA += p[3];
                Double avg = null;
                if (cumV > 0) {
                    double cand = cumA / cumV;
                    if (p[1] * 0.5 <= cand && cand <= p[1] * 2) {
                        avg = Math.round(cand * 1000) / 1000.0;
                    }
                }
                String time = String.format("%02d:%02d", (int) p[0] / 100, (int) p[0] % 100);
                points.add(trendPoint(time, p[1], avg, (long) p[2]));
            }
            Map<String, Object> dayData = new LinkedHashMap<>();
            dayData.put("date", day);
            dayData.put("prev_close", prevClose);
            dayData.put("points", points);
            built.add(dayData);
        }
        // 从最新日往回取 N 天（多取的一天仅用于昨收）
        List<Map<String, Object>> outDays = built.size() > days
            ? new ArrayList<>(built.subList(built.size() - days, built.size()))
            : built;
        if (outDays.isEmpty()) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("symbol", sym);
        out.put("scale", "5min");
        out.put("days", outDays);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asPoints(Object value) {
        return value instanceof List ? (List<Map<String, Object>>) value : List.of();
    }

    private static double asDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private static long asLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0;
    }

    /** 轻量 HTTP/JSON 助手（带自定义请求头） */
    static final class HttpRequestHelper {
        JsonNode getJson(String url, Map<String, String> headers, int timeoutSeconds) {
            try {
                java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                    .GET();
                headers.forEach(builder::header);
                java.net.http.HttpResponse<String> resp = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build()
                    .send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                return MarketUtils.MAPPER.readTree(resp.body());
            } catch (Exception e) {
                throw new IllegalStateException("请求失败: " + e.getMessage(), e);
            }
        }
    }
}
