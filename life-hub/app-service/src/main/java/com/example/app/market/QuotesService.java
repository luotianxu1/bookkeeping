package com.example.app.market;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多市场行情与板块（对应 investment 项目 services/quotes.py、indices.py、stocks.py、
 * sectors.py、global_modules.py、valuation.py、detail.py）。
 */
public final class QuotesService {

    private QuotesService() {
    }

    /** 海外市场：新浪实时行情代码前缀（K 线用腾讯） */
    private static final Map<String, String> OVERSEAS_QUOTE_PREFIX = Map.of(
        "global", "int_", "hk", "rt_hk", "us", "gb_"
    );

    // ---------- 分组行情 ----------

    /** 按分组取批量行情（新浪 int_ 前缀停更改腾讯快照），输出保持 config 配置顺序 */
    public static List<Map<String, Object>> marketQuotes(String group) {
        List<Map.Entry<String, String>> codes = MarketConfig.MARKET_GROUPS.get(group);
        if (codes == null) {
            throw new IllegalArgumentException("未知市场分组: " + group);
        }
        Map<String, String> tmap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : codes) {
            if (entry.getKey().startsWith("int_")) {
                String sym = TencentClient.worldTencentSymbol(entry.getKey());
                if (sym != null) {
                    tmap.put(entry.getKey(), sym);
                }
            }
        }
        List<String> tencentSymbols = tmap.values().stream().distinct().toList();
        Map<String, List<String>> tfields = tencentSymbols.isEmpty()
            ? Map.of() : TencentClient.tencentQt(tencentSymbols);
        List<String> sinaCodes = codes.stream()
            .map(Map.Entry::getKey).filter(code -> !tmap.containsKey(code)).toList();
        Map<String, List<String>> fields = sinaCodes.isEmpty() ? Map.of() : SinaClient.batchQuotes(sinaCodes);

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : codes) {
            String code = entry.getKey();
            Map<String, Object> row = null;
            if (tmap.containsKey(code)) {
                List<String> tf = tfields.get(tmap.get(code));
                if (tf != null) {
                    row = TencentClient.tencentQuoteRow(code, tf);
                    if (row != null) {
                        row.put("名称", entry.getValue()); // 腾讯行名称对齐配置
                    }
                }
            } else {
                List<String> f = fields.get(code);
                if (f != null) {
                    row = SinaClient.quoteRow(code, f, entry.getValue());
                }
            }
            if (row != null) {
                out.add(row);
            }
        }
        return out;
    }

    // ---------- 大盘指数 ----------

    /** 核心指数实时行情（新浪批量接口） */
    public static List<SinaClient.QuoteRow> indexSpot() {
        return SinaClient.codeRows(MarketConfig.SINA_INDEX_CODES);
    }

    /** 大盘汇总：沪深两市总成交额（元） + 全市场个股涨跌家数 */
    public static Map<String, Object> buildSummary(List<SinaClient.QuoteRow> indexRows) {
        double amount = 0;
        for (SinaClient.QuoteRow row : indexRows) {
            String code = String.valueOf(row.get("代码"));
            if (code.equals("000001") || code.equals("399001")) {
                Object amountValue = row.get("成交额");
                if (amountValue instanceof Number n) {
                    amount += n.doubleValue();
                }
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_amount", amount > 0 ? amount : null);
        summary.putAll(ActivityService.marketActivity("a"));
        return summary;
    }

    // ---------- A 股快照 ----------

    /** A 股个股快照（新浪列表接口，按成交额降序） */
    public static List<Map<String, Object>> stockSpot(int limit) {
        int num = Math.min(limit, 100);
        String url = "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/"
            + "Market_Center.getHQNodeData?page=1&num=" + num + "&sort=amount&asc=0&node=hs_a";
        com.fasterxml.jackson.databind.JsonNode items = SinaClient.getJson(url);
        List<Map<String, Object>> out = new ArrayList<>();
        if (items.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode it : items) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("代码", it.path("code").asText(""));
                row.put("名称", it.path("name").asText(""));
                row.put("最新价", MarketUtils.parseDouble(it.path("trade").asText("")));
                row.put("涨跌幅", MarketUtils.parseDouble(it.path("changepercent").asText("")));
                row.put("成交额", MarketUtils.parseDouble(it.path("amount").asText("")));
                out.add(row);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    // ---------- 板块行情 ----------

    /** 全部板块实时行情，按涨跌幅降序；kind: industry / concept */
    public static List<Map<String, Object>> sectorSpot(String kind) {
        String url = switch (kind) {
            case "industry" -> "https://vip.stock.finance.sina.com.cn/q/view/newSinaHy.php";
            case "concept" -> "https://vip.stock.finance.sina.com.cn/q/view/newFLJK.php?param=class";
            default -> throw new IllegalArgumentException("未知板块类型: " + kind);
        };
        com.fasterxml.jackson.databind.JsonNode raw = SinaClient.extractJsPayload(SinaClient.getText(url));
        List<Map<String, Object>> out = new ArrayList<>();
        raw.properties().forEach(entry -> {
            String[] f = entry.getValue().asText().split(",", -1);
            if (f.length < 13) {
                return;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("代码", f[0]);
            row.put("名称", f[1]);
            row.put("公司家数", MarketUtils.parseLongSafe(f[2]));
            row.put("平均价", SinaClient.parseNum(f[3]));
            row.put("涨跌额", SinaClient.parseNum(f[4]));
            row.put("涨跌幅", SinaClient.parseNum(f[5]));
            row.put("总成交量", SinaClient.parseNum(f[6]));
            row.put("总成交额", SinaClient.parseNum(f[7]));
            row.put("领涨股", f[12].trim());
            row.put("领涨股涨跌幅", SinaClient.parseNum(f[11]));
            out.add(row);
        });
        out.sort((a, b) -> Double.compare(orZero(a.get("涨跌幅")), orZero(b.get("涨跌幅"))));
        java.util.Collections.reverse(out);
        return out;
    }

    // ---------- 全球页六大模块 ----------

    /** 一次批量请求所有模块的新浪代码，按模块分组解析为统一行情行 */
    public static List<Map<String, Object>> globalModules() {
        List<String> allCodes = new ArrayList<>();
        for (MarketConfig.GlobalModuleDef module : MarketConfig.GLOBAL_MODULES) {
            for (Map.Entry<String, String> item : module.items()) {
                allCodes.add(item.getKey());
            }
        }
        Map<String, List<String>> fields = SinaClient.batchQuotes(allCodes);
        // int_ 品种 → 腾讯代码（int_nasdaq → usIXIC），批量快照
        Map<String, String> tmap = new LinkedHashMap<>();
        for (String code : allCodes) {
            if (code.startsWith("int_")) {
                String sym = TencentClient.worldTencentSymbol(code);
                if (sym != null) {
                    tmap.put(code, sym);
                }
            }
        }
        Map<String, List<String>> tfields = tmap.isEmpty()
            ? Map.of() : TencentClient.tencentQt(tmap.values().stream().distinct().toList());

        List<Map<String, Object>> out = new ArrayList<>();
        for (MarketConfig.GlobalModuleDef module : MarketConfig.GLOBAL_MODULES) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map.Entry<String, String> item : module.items()) {
                String code = item.getKey();
                Map<String, Object> row = null;
                if (tmap.containsKey(code)) {
                    List<String> tf = tfields.get(tmap.get(code));
                    if (tf != null) {
                        row = TencentClient.tencentQuoteRow(code, tf);
                    }
                } else {
                    List<String> f = fields.get(code);
                    if (f != null) {
                        row = SinaClient.quoteRow(code, f, item.getValue());
                    }
                }
                if (row != null) {
                    row.put("名称", item.getValue()); // 展示名称对齐需求
                    row.put("源代码", code);
                    // A股指数模块走 kind=index 详情（有分时/分钟线），其余模块有K线源才可点
                    row.put("linkable", module.key().equals("cn_index")
                        || !KlineService.MODULE_NO_KLINE.contains(code));
                    rows.add(row);
                }
            }
            Map<String, Object> moduleOut = new LinkedHashMap<>();
            moduleOut.put("key", module.key());
            moduleOut.put("title", module.title());
            moduleOut.put("linkable", module.linkable());
            moduleOut.put("rows", rows);
            out.add(moduleOut);
        }
        return out;
    }

    // ---------- 估值资料 ----------

    /** 腾讯 qt 估值字段（A股/港股适用）；非 stock/hk 或无数据返回 null */
    public static Map<String, Object> valuation(String kind, String code) {
        String sym;
        if (kind.equals("stock")) {
            sym = KlineService.sinaSymbol("stock", code);
        } else if (kind.equals("hk")) {
            sym = "hk" + code;
        } else {
            return null;
        }
        String text;
        try {
            text = TencentClient.httpGetText("https://qt.gtimg.cn/q=" + sym, 10);
        } catch (Exception e) {
            return null;
        }
        List<String> fields = null;
        for (String line : text.split(";")) {
            if (line.contains("v_" + sym + "=\"")) {
                String payload = line.split("=", 2)[1].trim();
                payload = payload.startsWith("\"") ? payload.substring(1) : payload;
                payload = payload.endsWith("\"") ? payload.substring(0, payload.length() - 1) : payload;
                fields = List.of(payload.split("~", -1));
                break;
            }
        }
        if (fields == null || fields.size() < 46 || fields.get(3).isEmpty()) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        putIfNotNull(out, "市盈率", zeroToNull(SinaClient.parseNum(fields.get(39))));
        putIfNotNull(out, "振幅", zeroToNull(SinaClient.parseNum(fields.get(43))));
        putIfNotNull(out, "流通市值", zeroToNull(SinaClient.parseNum(fields.get(44))));
        putIfNotNull(out, "总市值", zeroToNull(SinaClient.parseNum(fields.get(45))));
        if (kind.equals("stock")) {
            putIfNotNull(out, "市净率", zeroToNull(SinaClient.parseNum(fields.get(46))));
            putIfNotNull(out, "换手率", zeroToNull(SinaClient.parseNum(fields.get(38))));
        } else {
            if (fields.size() > 48) {
                putIfNotNull(out, "52周最高", zeroToNull(SinaClient.parseNum(fields.get(48))));
            }
            if (fields.size() > 49) {
                putIfNotNull(out, "52周最低", zeroToNull(SinaClient.parseNum(fields.get(49))));
            }
        }
        return out.isEmpty() ? null : out;
    }

    // ---------- 详情聚合 ----------

    /** (kind, code) → 新浪实时行情代码 */
    public static String quoteSymbol(String kind, String code) {
        if (kind.equals("gold")) {
            return KlineService.goldSinaSymbol(code);
        }
        if (kind.equals("module")) {
            return code;
        }
        String prefix = OVERSEAS_QUOTE_PREFIX.get(kind);
        if (prefix != null) {
            String suffix = kind.equals("us") ? code.toLowerCase() : code;
            return prefix + suffix;
        }
        return KlineService.sinaSymbol(kind, code);
    }

    /** 单个标的的实时行情 + 五档/估值 + K 线序列 */
    public static Map<String, Object> detail(String kind, String code, String scale, int datalen) {
        String sym = quoteSymbol(kind, code);
        // 新浪 int_（美股指数）行情数据已停更：module 的 int_ 代码与美股页指数改腾讯快照
        String intCode = null;
        if (kind.equals("module") && code.startsWith("int_")) {
            intCode = code;
        } else if (kind.equals("us") && MarketConfig.GLOBAL_TENCENT_CODES.containsKey(code.toLowerCase())) {
            intCode = "int_" + code.toLowerCase();
        }

        Map<String, Object> quote = null;
        List<String> fields = null;
        if (intCode != null) {
            String tsym = TencentClient.worldTencentSymbol(intCode);
            List<String> tf = tsym != null ? TencentClient.tencentQt(List.of(tsym)).get(tsym) : null;
            if (tf != null) {
                quote = TencentClient.tencentQuoteRow(intCode, tf);
            }
        } else {
            fields = SinaClient.batchQuotes(List.of(sym)).get(sym);
            if (fields != null) {
                quote = SinaClient.quoteRow(sym, fields, "");
            }
        }
        // 五档仅 A 股标的（海外前缀 parseDepth 内部返回 null）
        Map<String, Object> depth = fields != null ? SinaClient.parseDepth(sym, fields) : null;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", kind);
        out.put("code", code);
        out.put("symbol", sym);
        out.put("quote", quote);
        out.put("depth", depth);
        out.put("valuation", valuation(kind, code));
        out.put("kline", KlineService.kline(kind, code, scale, datalen));
        return out;
    }

    private static Double zeroToNull(Double v) {
        return v == null || v == 0 ? null : v;
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static double orZero(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }
}
