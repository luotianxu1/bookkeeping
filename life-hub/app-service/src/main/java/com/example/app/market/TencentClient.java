package com.example.app.market;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 腾讯财经数据客户端（对应 investment 项目 services/kline.py 中 tencent_qt 等）。
 */
public final class TencentClient {

    private TencentClient() {
    }

    private static final Charset GBK = Charset.forName("GBK");
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    /**
     * 腾讯 qt.gtimg.cn 批量快照：{代码: 字段数组}。
     * 字段：[1]名称 [3]最新 [4]昨收 [5]今开 [30]时间 [31]涨跌额 [32]涨跌幅 [33]最高 [34]最低
     */
    public static Map<String, List<String>> tencentQt(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Map.of();
        }
        String text = httpGetText("https://qt.gtimg.cn/q=" + String.join(",", codes), GBK);
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String line : text.trim().split(";")) {
            if (!line.contains("=") || !line.contains("\"")) {
                continue;
            }
            String sym = line.split("=", 2)[0].replace("v_", "").trim();
            String payload = line.split("\"")[1];
            if (!payload.isEmpty()) {
                out.put(sym, new ArrayList<>(List.of(payload.split("~", -1))));
            }
        }
        return out;
    }

    /** 腾讯快照 → 统一行情行（美股指数 int_ 用；新浪 int_ 行情数据已停更） */
    public static SinaClient.QuoteRow tencentQuoteRow(String code, List<String> f) {
        try {
            double cur = Double.parseDouble(f.get(3).trim());
            double prev = Double.parseDouble(f.get(4).trim());
            Double chg = f.size() > 31 && !f.get(31).isEmpty()
                ? Double.parseDouble(f.get(31).trim())
                : (prev != 0 ? cur - prev : null);
            Double pct = f.size() > 32 && !f.get(32).isEmpty()
                ? Double.parseDouble(f.get(32).trim())
                : (prev != 0 ? (cur - prev) / prev * 100 : null);
            SinaClient.QuoteRow row = new SinaClient.QuoteRow();
            row.put("代码", code.startsWith("int_") ? code.substring(4) : code);
            row.put("名称", f.get(1) == null || f.get(1).isEmpty() ? code : f.get(1));
            row.put("最新价", cur);
            row.put("涨跌额", chg);
            row.put("涨跌幅", pct == null ? null : Math.round(pct * 100) / 100.0);
            row.put("成交额", null);
            return row;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 全球指数（int_/rt_hk 前缀）→ 腾讯代码（行情快照/K线/分时共用）；znb_ 无源返回 null */
    public static String worldTencentSymbol(String code) {
        if (code.startsWith("int_")) {
            return MarketConfig.GLOBAL_TENCENT_CODES.get(code.substring(4).toLowerCase());
        }
        if (code.startsWith("rt_hk")) {
            return "hk" + code.substring(5);
        }
        return null;
    }

    /** GBK 文本 GET */
    public static String httpGetText(String url, int timeoutSeconds) {
        return httpGetText(url, Charset.forName("GBK"), timeoutSeconds);
    }

    public static String httpGetText(String url, Charset charset) {
        return httpGetText(url, charset, 10);
    }

    public static String httpGetText(String url, Charset charset, int timeoutSeconds) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .headers(MarketConfig.TENCENT_HEADERS.entrySet().stream()
                    .flatMap(e -> java.util.stream.Stream.of(e.getKey(), e.getValue()))
                    .toArray(String[]::new))
                .GET()
                .build();
            HttpResponse<byte[]> resp = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("腾讯接口返回 " + resp.statusCode());
            }
            return new String(resp.body(), charset);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("腾讯接口请求失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求被中断", e);
        }
    }

    /** JSON GET（UTF-8） */
    public static String httpGetJson(String url) {
        return httpGetText(url, java.nio.charset.StandardCharsets.UTF_8);
    }
}
