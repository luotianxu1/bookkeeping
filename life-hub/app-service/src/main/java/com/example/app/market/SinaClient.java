package com.example.app.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新浪行情数据客户端（对应 investment 项目 services/sina.py）。
 *
 * <p>负责与 hq.sinajs.cn / vip.stock.finance.sina.com.cn 通信，
 * 把不同前缀（int_/rt_hk/gb_/hf_/nf_/A股）的原始字段解析为统一行情行。</p>
 */
public final class SinaClient {

    private SinaClient() {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Charset GBK = Charset.forName("GBK");

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    /** 行情行字段：代码/名称/最新价/涨跌额/涨跌幅/成交额（部分行含 最高/最低/成交量） */
    public static final class QuoteRow extends LinkedHashMap<String, Object> {
    }

    /** 字符串转浮点，失败返回 null */
    public static Double parseNum(String x) {
        if (x == null) {
            return null;
        }
        try {
            return Double.parseDouble(x.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** GET 并按指定编码返回文本 */
    public static String getText(String url) {
        return getText(url, GBK, 10);
    }

    public static String getText(String url, Charset charset, int timeoutSeconds) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .headers(MarketConfig.SINA_HEADERS.entrySet().stream()
                .flatMap(e -> java.util.stream.Stream.of(e.getKey(), e.getValue()))
                .toArray(String[]::new))
            .GET()
            .build();
        try {
            HttpResponse<byte[]> resp = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("新浪接口返回 " + resp.statusCode());
            }
            return new String(resp.body(), charset);
        } catch (IOException e) {
            throw new IllegalStateException("新浪接口请求失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求被中断", e);
        }
    }

    /** GET 并解析 JSON（新浪列表接口返回的是 JSON 文本） */
    public static JsonNode getJson(String url) {
        try {
            return MAPPER.readTree(getText(url, StandardCharsets.UTF_8, 10));
        } catch (IOException e) {
            throw new IllegalStateException("新浪 JSON 解析失败: " + e.getMessage(), e);
        }
    }

    /** hq.sinajs.cn 批量行情，返回 {代码: 字段数组}，缺失/空的代码不出现 */
    public static Map<String, List<String>> batchQuotes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Map.of();
        }
        String text = getText("https://hq.sinajs.cn/list=" + String.join(",", codes));
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String code : codes) {
            Matcher m = Pattern.compile("hq_str_" + Pattern.quote(code) + "=\"([^\"]*)\"").matcher(text);
            if (m.find() && !m.group(1).isEmpty()) {
                List<String> fields = new ArrayList<>(List.of(m.group(1).split(",", -1)));
                out.put(code, fields);
            }
        }
        return out;
    }

    /**
     * 按新浪不同前缀解析为统一行情行：
     * {代码, 名称, 最新价, 涨跌额, 涨跌幅, 成交额[, 最高, 最低, 成交量]}
     */
    public static QuoteRow quoteRow(String code, List<String> f, String defaultName) {
        try {
            if (code.startsWith("int_")) {
                // 全球指数: 名称,最新,涨跌额,涨跌幅
                if (f.size() < 4 || f.get(1).isEmpty()) {
                    return null;
                }
                QuoteRow row = new QuoteRow();
                row.put("代码", code.substring(4));
                row.put("名称", f.get(0).isEmpty() ? defaultName : f.get(0));
                row.put("最新价", parseNum(f.get(1)));
                row.put("涨跌额", parseNum(f.get(2)));
                row.put("涨跌幅", parseNum(f.get(3)));
                row.put("成交额", null);
                return row;
            }
            if (code.startsWith("rt_hk")) {
                // 港股: f1名称 f3昨收 f6最新 f7涨跌额 f8涨跌幅 f12成交额
                if (f.size() < 13 || f.get(6).isEmpty()) {
                    return null;
                }
                QuoteRow row = new QuoteRow();
                row.put("代码", code.substring(5));
                row.put("名称", f.get(1).isEmpty() ? defaultName : f.get(1));
                row.put("最新价", parseNum(f.get(6)));
                row.put("涨跌额", parseNum(f.get(7)));
                row.put("涨跌幅", parseNum(f.get(8)));
                row.put("成交额", parseNum(f.get(12)));
                return row;
            }
            if (code.startsWith("gb_")) {
                // 美股: f0名称 f1最新 f2涨跌幅 f4涨跌额
                if (f.size() < 5 || f.get(1).isEmpty()) {
                    return null;
                }
                QuoteRow row = new QuoteRow();
                row.put("代码", code.substring(3).toUpperCase());
                row.put("名称", f.get(0).isEmpty() ? defaultName : f.get(0));
                row.put("最新价", parseNum(f.get(1)));
                row.put("涨跌额", parseNum(f.get(4)));
                row.put("涨跌幅", parseNum(f.get(2)));
                row.put("成交额", null);
                return row;
            }
            if (code.startsWith("hf_")) {
                // 外盘期货: f0最新 f7昨收 f13名称
                if (f.size() < 14 || f.get(0).isEmpty()) {
                    return null;
                }
                Double last = parseNum(f.get(0));
                Double prev = parseNum(f.get(7));
                QuoteRow row = new QuoteRow();
                row.put("代码", code.substring(3));
                String name = f.get(13).split("（")[0];
                row.put("名称", name.isEmpty() ? defaultName : name);
                row.put("最新价", last);
                row.put("涨跌额", last != null && prev != null ? round3(last - prev) : null);
                row.put("涨跌幅", last != null && prev != null && prev != 0 ? round3((last - prev) / prev * 100) : null);
                row.put("成交额", null);
                return row;
            }
            if (code.startsWith("nf_")) {
                // 国内期货：中金所 50 字段布局 f3最新 f48昨结算 f49名称
                if (f.size() >= 50) {
                    if (f.get(3).isEmpty()) {
                        return null;
                    }
                    Double last = parseNum(f.get(3));
                    Double prev = parseNum(f.get(48));
                    QuoteRow row = new QuoteRow();
                    row.put("代码", code.substring(3));
                    row.put("名称", f.get(49).isEmpty() ? defaultName : f.get(49));
                    row.put("最新价", last);
                    row.put("涨跌额", last != null && prev != null ? round3(last - prev) : null);
                    row.put("涨跌幅", last != null && prev != null && prev != 0 ? round3((last - prev) / prev * 100) : null);
                    row.put("成交额", null);
                    return row;
                }
                // 其他交易所（AU0/AG0/CU0/SC0…）: f0名称 f8最新 f10昨结算
                if (f.size() < 11 || f.get(8).isEmpty()) {
                    return null;
                }
                Double last = parseNum(f.get(8));
                Double prev = parseNum(f.get(10));
                QuoteRow row = new QuoteRow();
                row.put("代码", code.substring(3));
                row.put("名称", f.get(0).isEmpty() ? defaultName : f.get(0));
                row.put("最新价", last);
                row.put("涨跌额", last != null && prev != null ? round3(last - prev) : null);
                row.put("涨跌幅", last != null && prev != null && prev != 0 ? round3((last - prev) / prev * 100) : null);
                row.put("成交额", null);
                return row;
            }
            if (code.startsWith("fx_") || code.equals("DINI")) {
                // 外汇/美元指数: f3昨收 f8最新 f9名称 f10涨跌幅 f11涨跌额
                if (f.size() < 10 || f.get(8).isEmpty()) {
                    return null;
                }
                Double last = parseNum(f.get(8));
                Double prev = parseNum(f.get(3));
                Double pctV = f.size() > 10 ? parseNum(f.get(10)) : null;
                Double chgV = f.size() > 11 ? parseNum(f.get(11)) : null;
                if (pctV == null && last != null && prev != null && prev != 0) {
                    pctV = round3((last - prev) / prev * 100);
                }
                if (chgV == null && last != null && prev != null) {
                    chgV = round5(last - prev);
                }
                QuoteRow row = new QuoteRow();
                row.put("代码", code);
                row.put("名称", f.get(9).isEmpty() ? defaultName : f.get(9));
                row.put("最新价", last);
                row.put("涨跌额", chgV);
                row.put("涨跌幅", pctV);
                row.put("成交额", null);
                return row;
            }
            if (code.startsWith("znb_")) {
                // 全球指数(实时): f0名称 f1最新 f2涨跌额 f3涨跌幅
                if (f.size() < 4 || f.get(1).isEmpty()) {
                    return null;
                }
                QuoteRow row = new QuoteRow();
                row.put("代码", code.substring(4));
                row.put("名称", f.get(0).isEmpty() ? defaultName : f.get(0));
                row.put("最新价", parseNum(f.get(1)));
                row.put("涨跌额", parseNum(f.get(2)));
                row.put("涨跌幅", parseNum(f.get(3)));
                row.put("成交额", null);
                return row;
            }
            // A 股 / ETF: f0名称 f1今开 f2昨收 f3最新 f4高 f5低 f8成交量 f9成交额
            if (f.size() < 10 || f.get(3).isEmpty()) {
                return null;
            }
            Double last = parseNum(f.get(3));
            Double prev = parseNum(f.get(2));
            QuoteRow row = new QuoteRow();
            row.put("代码", code.substring(2));
            row.put("名称", f.get(0).isEmpty() ? defaultName : f.get(0));
            row.put("最新价", last);
            row.put("开盘", parseNum(f.get(1)));
            row.put("涨跌额", last != null && prev != null ? round3(last - prev) : null);
            row.put("涨跌幅", last != null && prev != null && prev != 0 ? round3((last - prev) / prev * 100) : null);
            row.put("最高", parseNum(f.get(4)));
            row.put("最低", parseNum(f.get(5)));
            row.put("成交量", parseNum(f.get(8)));
            row.put("成交额", parseNum(f.get(9)));
            return row;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 批量取一组 (代码, 默认名称) 并解析为统一行情行列表 */
    public static List<QuoteRow> codeRows(List<Map.Entry<String, String>> codes) {
        List<String> symbols = codes.stream().map(Map.Entry::getKey).toList();
        Map<String, List<String>> fields = batchQuotes(symbols);
        List<QuoteRow> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : codes) {
            List<String> f = fields.get(entry.getKey());
            if (f == null) {
                continue;
            }
            QuoteRow row = quoteRow(entry.getKey(), f, entry.getValue());
            if (row != null) {
                out.add(row);
            }
        }
        return out;
    }

    /** A股五档盘口：f10-f19 买五档（量,价）、f20-f29 卖五档（量,价），量单位股 */
    public static Map<String, Object> parseDepth(String code, List<String> f) {
        if (code.startsWith("int_") || code.startsWith("rt_hk") || code.startsWith("gb_")
            || code.startsWith("hf_") || code.startsWith("nf_") || code.startsWith("fx_")
            || code.startsWith("znb_") || code.equals("DINI")) {
            return null;
        }
        if (f.size() < 30 || f.get(3).isEmpty()) {
            return null;

        }
        List<List<Object>> bids = new ArrayList<>();
        List<List<Object>> asks = new ArrayList<>();
        for (int i = 10; i < 20; i += 2) {
            List<Object> pair = depthPair(f, i);
            if (pair == null) {
                return null;
            }
            bids.add(pair);
        }
        for (int i = 20; i < 30; i += 2) {
            List<Object> pair = depthPair(f, i);
            if (pair == null) {
                return null;
            }
            asks.add(pair);
        }
        // 指数等无盘口标的五档全为 0
        boolean allZero = bids.stream().allMatch(p -> num(p.get(0)) == 0 && num(p.get(1)) == 0)
            && asks.stream().allMatch(p -> num(p.get(0)) == 0 && num(p.get(1)) == 0);
        if (allZero) {
            return null;
        }
        Map<String, Object> depth = new LinkedHashMap<>();
        depth.put("bids", bids);
        depth.put("asks", asks);
        depth.put("date", f.size() > 30 ? f.get(30) : "");
        depth.put("time", f.size() > 31 ? f.get(31) : "");
        return depth;
    }

    private static List<Object> depthPair(List<String> f, int i) {
        Double v = parseNum(f.get(i));
        Double p = parseNum(f.get(i + 1));
        if (p == null || v == null) {
            return null;
        }
        // [价格, 挂单量(股)]
        return List.of(p, v);
    }

    /** 解析新浪 `var X = {...};` 形式的 JS 变量为 JsonNode */
    public static JsonNode extractJsPayload(String text) {
        try {
            String payload = text.substring(text.indexOf('=') + 1).trim();
            if (payload.endsWith(";")) {
                payload = payload.substring(0, payload.length() - 1);
            }
            return MAPPER.readTree(payload);
        } catch (IOException e) {
            throw new IllegalStateException("新浪 JS 载荷解析失败: " + e.getMessage(), e);
        }
    }

    private static double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private static double round5(double v) {
        return Math.round(v * 100000) / 100000.0;
    }
}
