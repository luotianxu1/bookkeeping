package com.example.market.fund;

import com.example.market.market.TencentClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 腾讯财经报价数据源：
 * - smartbox.gtimg.cn 搜索联想（股票/基金/港股混合，按前缀与类型字母归一）
 * - qt.gtimg.cn A股快照（名称/现价，替代东财 push2 f57/f58/f43）
 */
final class TencentQuoteClient {

    private TencentQuoteClient() {
    }

    /** 搜索联想：item: code/name/type(fund|stock)/exchange(SSE|SZSE)，其余市场丢弃 */
    static List<Map<String, Object>> search(String keyword) throws Exception {
        String url = "https://smartbox.gtimg.cn/s3/?v=2&q=" + java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8) + "&t=all";
        String text = TencentClient.httpGetText(url, FundHttp.GBK, 8);
        List<Map<String, Object>> items = new ArrayList<>();
        Matcher all = Pattern.compile("v_hint=\"(.*)\"").matcher(text);
        if (!all.find()) {
            return items;
        }
        for (String entry : all.group(1).split("\\^")) {
            String[] f = entry.split("~");
            if (f.length < 3) {
                continue;
            }
            String market = f[0].trim().toLowerCase();
            String code = f[1].trim();
            String name = f[2].trim();
            String type = f.length > 4 ? f[4].trim().toUpperCase() : "";
            String resolvedType;
            String exchange = null;
            switch (market) {
                case "jj" -> resolvedType = "fund";
                case "sh" -> {
                    resolvedType = "stock";
                    exchange = "SSE";
                }
                case "sz" -> {
                    resolvedType = "stock";
                    exchange = "SZSE";
                }
                default -> {
                    continue;
                }
            }
            // LOF/ETF 场内份额归为基金（与原东财 suggest type=14 口径一致）
            if ("stock".equals(resolvedType) && (type.contains("LOF") || type.contains("ETF") || name.contains("基金"))) {
                resolvedType = "fund";
            }
            // 指数（ZS）与原逻辑一致：不入库为可交易品种，丢弃
            if ("stock".equals(resolvedType) && type.contains("ZS")) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", code);
            item.put("name", name);
            item.put("type", resolvedType);
            item.put("exchange", exchange);
            items.add(item);
        }
        return items;
    }

    /** A股快照：尝试沪深两个前缀，取有报价的那个 */
    static Map<String, Object> stockSnapshot(String code) {
        List<String> candidates = code.startsWith("6") || code.startsWith("9")
            ? List.of("sh" + code, "sz" + code)
            : List.of("sz" + code, "sh" + code);
        Map<String, List<String>> quotes = TencentClient.tencentQt(candidates);
        for (Map.Entry<String, List<String>> e : quotes.entrySet()) {
            List<String> f = e.getValue();
            if (f.size() < 4 || f.get(1) == null || f.get(1).isBlank()) {
                continue;
            }
            String price = f.get(3);
            if (price == null || !price.matches("\\d+(?:\\.\\d+)?") || Double.parseDouble(price) <= 0) {
                continue;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("matched", true);
            out.put("code", code);
            out.put("name", f.get(1).trim());
            out.put("price", price);
            out.put("prevClose", f.size() > 4 ? f.get(4) : null);
            out.put("open", f.size() > 5 ? f.get(5) : null);
            out.put("volume", f.size() > 6 ? f.get(6) : null);
            out.put("exchangeCode", e.getKey().startsWith("sh") ? "SSE" : "SZSE");
            out.put("quoteTime", f.size() > 30 ? f.get(30) : null);
            out.put("timeRaw", f.size() > 30 ? f.get(30) : null);
            out.put("change", f.size() > 31 ? f.get(31) : null);
            out.put("changePercent", f.size() > 32 ? f.get(32) : null);
            out.put("high", f.size() > 33 ? f.get(33) : null);
            out.put("low", f.size() > 34 ? f.get(34) : null);
            out.put("turnoverRate", f.size() > 38 ? f.get(38) : null);
            out.put("pe", f.size() > 39 ? f.get(39) : null);
            return out;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("matched", false);
        return out;
    }
}
