package com.example.market.market;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * F10 扩展信息（详情页）：财务指标 / 十大流通股东 / 三大报表 / 公司概况 / 所属板块（新浪 F10 页面解析）
 * + 融资融券（沪深交易所官方数据）。（对应 investment 项目 services/f10.py）
 *
 * <p>财务指标：money.finance.sina.com.cn vFD_FinancialGuideLine（按年分页，取最新报告期 + 上年同期）；
 * 三大报表：vFD_ProfitStatement / vFD_BalanceSheet / vFD_CashFlow；
 * 公司概况与股东：vip.stock.finance.sina.com.cn vCI_CorpInfo / vCI_StockHolder；
 * 融资融券：query.sse.com.cn queryMargin.do JSON（重试 2 次）+ www.szse.cn ShowReport xlsx
 * （xlsx 即 zip 包，内置 Zip + XML 解析，无 POI 依赖）。</p>
 */
public final class F10Service {

    private F10Service() {
    }

    private static final int TIMEOUT = 15;
    private static final Charset GBK = Charset.forName("GBK");

    private static final String FIN_URL =
        "https://money.finance.sina.com.cn/corp/go.php/vFD_FinancialGuideLine"
            + "/stockid/{code}/ctrl/{year}/displaytype/4.phtml";
    private static final String HOLDER_URL =
        "https://vip.stock.finance.sina.com.cn/corp/go.php/vCI_StockHolder/stockid/{code}.phtml";
    private static final String PROFILE_URL =
        "https://vip.stock.finance.sina.com.cn/corp/go.php/vCI_CorpInfo/stockid/{code}.phtml";
    private static final String SECTORS_URL =
        "https://vip.stock.finance.sina.com.cn/corp/go.php/vCI_CorpOtherInfo/stockid/{code}/num_menu.phtml";
    private static final String SIBLING_URL =
        "https://vip.stock.finance.sina.com.cn/corp/view/vCI_CorpInfoLink.php?page=1&stockid={code}&Type={type_id}";

    // 三大报表：key -> 页面 URL（统一用 NewTable0 兜底）
    private static final Map<String, String> STATEMENT_URLS = new LinkedHashMap<>();
    private static final Map<String, String> STATEMENT_TITLES = new LinkedHashMap<>();

    static {
        STATEMENT_URLS.put("income",
            "https://money.finance.sina.com.cn/corp/go.php/vFD_ProfitStatement/stockid/{code}/ctrl/{year}/displaytype/4.phtml");
        STATEMENT_URLS.put("balance",
            "https://money.finance.sina.com.cn/corp/go.php/vFD_BalanceSheet/stockid/{code}/ctrl/{year}/displaytype/4.phtml");
        STATEMENT_URLS.put("cashflow",
            "https://money.finance.sina.com.cn/corp/go.php/vFD_CashFlow/stockid/{code}/ctrl/{year}/displaytype/4.phtml");
        STATEMENT_TITLES.put("income", "利润表");
        STATEMENT_TITLES.put("balance", "资产负债表");
        STATEMENT_TITLES.put("cashflow", "现金流量表");
    }

    /** 财务指标：显示名 -> 候选行标签（优先精确，再前缀模糊；新浪该表无营收/净利润裸行，用主营利润/扣非净利润） */
    private static final String[][] FIN_INDICATORS = {
        {"每股收益(元)", "摊薄每股收益(元)", "每股收益(元)"},
        {"加权每股收益(元)", "加权每股收益(元)"},
        {"净资产收益率(%)", "净资产收益率(%)"},
        {"每股净资产(元)", "每股净资产(元)", "每股净资产_调整前(元)"},
        {"主营利润(元)", "主营业务利润(元)", "营业利润(元)"},
        {"扣非净利润(元)", "扣除非经常性损益后的净利润(元)", "净利润(元)"},
        {"总资产(元)", "总资产(元)"},
    };
    /** 模糊回退时排除的比率/衍生类行 */
    private static final Pattern BAD_LABEL =
        Pattern.compile("增长率|比例|利润率|报酬率|周转|天数|倍数|比重|现金");
    private static final Pattern DATE_ONLY = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20)).build();

    // ---------------------------------------------------------------- 公共解析工具

    private static String httpText(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT))
                .header("User-Agent", MarketConfig.SINA_HEADERS.get("User-Agent"))
                .header("Referer", MarketConfig.SINA_HEADERS.get("Referer"))
                .GET().build();
            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + resp.statusCode());
            }
            return new String(resp.body(), GBK);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String clean(String text) {
        return text.replaceAll("<[^>]*>", "").replaceAll("[\\s\\u00a0]+", "");
    }

    private static List<String> cells(String rowHtml) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL).matcher(rowHtml);
        while (m.find()) {
            out.add(clean(m.group(1)));
        }
        return out;
    }

    private static List<String> rows(String html) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL).matcher(html);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static Double parseCell(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double num(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(text.replace(",", ""));
        return m.find() ? Double.parseDouble(m.group(0)) : null;
    }

    private static Double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** 页面日期行 -> 仅保留 YYYY-MM-DD 的单元格 */
    private static List<String> dateCells(List<String> cs) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i < cs.size(); i++) {
            if (DATE_ONLY.matcher(cs.get(i)).matches()) {
                out.add(cs.get(i));
            }
        }
        return out;
    }

    /** lru_cache 等价物：按日 LRU 缓存（容量 64），非交易日结果不缓存 */
    private static final Map<String, Map<String, Map<String, Object>>> SSE_CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static final Map<String, Map<String, Map<String, Object>>> SZSE_CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static final Object CACHE_LOCK = new Object();

    private interface DayFetcher {
        Map<String, Map<String, Object>> fetch(String day) throws Exception;
    }

    private static Map<String, Map<String, Object>> cachedDay(
        Map<String, Map<String, Map<String, Object>>> cache, String day, DayFetcher fetcher) {
        synchronized (CACHE_LOCK) {
            Map<String, Map<String, Object>> hit = cache.get(day);
            if (hit != null) {
                return hit;
            }
        }
        Map<String, Map<String, Object>> value;
        try {
            value = fetcher.fetch(day);
        } catch (Exception e) {
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e.getMessage(), e);
        }
        if (value.isEmpty()) {
            throw new IllegalStateException("无融资融券数据（非交易日）");
        }
        synchronized (CACHE_LOCK) {
            if (cache.size() >= 64) {
                for (String oldest : cache.keySet()) {
                    cache.remove(oldest);
                    break;
                }
            }
            cache.put(day, value);
        }
        return value;
    }

    // ---------------------------------------------------------------- 财务指标

    /** 一页（某年）-> {报告期日期: {行标签: 值}}；标签顺序为 {行标签: {报告期: 值}}，见 financials 翻转 */
    private static Map<String, Map<String, Double>> parseFinPage(String html) {
        Map<String, Map<String, Double>> table = new LinkedHashMap<>();
        Matcher m = Pattern.compile("<table id=\"BalanceSheetNewTable0\".*?</table>", Pattern.DOTALL)
            .matcher(html);
        if (!m.find()) {
            return table;
        }
        List<String> dates = new ArrayList<>();
        for (String tr : rows(m.group())) {
            List<String> cs = cells(tr);
            if (cs.isEmpty()) {
                continue;
            }
            if (cs.get(0).equals("报告日期")) {
                dates = dateCells(cs);
                continue;
            }
            if (dates.isEmpty() || cs.size() < 2) {
                continue; // 跳过分组标题行（每股指标 等 colspan 行）
            }
            String label = cs.get(0);
            Map<String, Double> vals = table.computeIfAbsent(label, k -> new LinkedHashMap<>());
            for (int i = 0; i < dates.size(); i++) {
                vals.put(dates.get(i), i + 1 < cs.size() ? parseCell(cs.get(i + 1)) : null);
            }
        }
        return table;
    }

    private static Map<String, Map<String, Double>> finPage(int year, String code) {
        return parseFinPage(httpText(FIN_URL
            .replace("{code}", code).replace("{year}", String.valueOf(year))));
    }

    /** 候选行取值：精确匹配 -> 首候选 stem 前缀模糊（排除比率/衍生类行） */
    private static Double pickRow(Map<String, Double> labels, String[] candidates) {
        for (String c : candidates) {
            if (labels.containsKey(c)) {
                return labels.get(c);
            }
        }
        String stem = candidates[0].split("\\(")[0];
        for (Map.Entry<String, Double> e : labels.entrySet()) {
            if (e.getKey().startsWith(stem) && !BAD_LABEL.matcher(e.getKey()).find()) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 最新报告期核心财务指标 + 上年同期对比 */
    public static Map<String, Object> financials(String code) {
        int year = LocalDate.now().getYear();
        // finPage 为 {指标标签: {报告期: 值}}，翻转为 {报告期: {标签: 值}} 并跨年合并
        Map<String, Map<String, Double>> merged = new LinkedHashMap<>();
        for (int y : new int[]{year, year - 1}) {
            for (Map.Entry<String, Map<String, Double>> e : finPage(y, code).entrySet()) {
                for (Map.Entry<String, Double> d : e.getValue().entrySet()) {
                    merged.computeIfAbsent(d.getKey(), k -> new LinkedHashMap<>()).put(e.getKey(), d.getValue());
                }
            }
        }
        if (merged.isEmpty()) {
            return null;
        }
        String latest = merged.keySet().stream().max(String::compareTo).orElse(null);
        String prev = (Integer.parseInt(latest.substring(0, 4)) - 1) + latest.substring(4);

        List<Map<String, Object>> out = new ArrayList<>();
        for (String[] ind : FIN_INDICATORS) {
            Double cur = pickRow(merged.getOrDefault(latest, Map.of()),
                java.util.Arrays.copyOfRange(ind, 1, ind.length));
            Double old = pickRow(merged.getOrDefault(prev, Map.of()),
                java.util.Arrays.copyOfRange(ind, 1, ind.length));
            if (cur == null && old == null) {
                continue;
            }
            Double yoy = null;
            if (cur != null && old != null && old != 0) {
                yoy = round2((cur - old) / Math.abs(old) * 100);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", ind[0]);
            row.put("latest", cur);
            row.put("prev", old);
            row.put("yoy", yoy);
            out.add(row);
        }
        if (out.isEmpty()) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("latest_date", latest);
        result.put("prev_date", prev);
        result.put("rows", out);
        return result;
    }

    // ---------------------------------------------------------------- 十大流通股东

    /** 最新报告期十大流通股东（新浪 F10 股东页） */
    public static Map<String, Object> holders(String kind, String code) {
        if (!"stock".equals(kind)) {
            return null;
        }
        String html = httpText(HOLDER_URL.replace("{code}", code));
        if (!html.contains("主要股东")) {
            return null;
        }
        // 报告期以 <a name="YYYY-MM-DD"> 分块，页面按报告期从新到旧排列
        List<String> parts = new ArrayList<>();
        Matcher pm = Pattern.compile("<a name=\"(20\\d{2}-\\d{2}-\\d{2})\">").matcher(html);
        int last = 0;
        parts.add("");
        while (pm.find()) {
            parts.add(pm.group(1));
            parts.add(html.substring(last, pm.start()));
            last = pm.end();
        }
        parts.add(html.substring(last));
        if (parts.size() < 3) {
            return null;
        }
        String blockDate = parts.get(1);
        String block = parts.get(2);

        String announce = null;
        Double total = null;
        // 各报告期股东总数（页面顺序 = 从新到旧）
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int pi = 1; pi + 1 < parts.size(); pi += 2) {
            String pDate = parts.get(pi);
            for (String tr : rows(parts.get(pi + 1))) {
                List<String> cs = cells(tr);
                if (!cs.isEmpty() && cs.get(0).equals("股东总数") && cs.size() > 1) {
                    Double val = num(cs.get(1));
                    if (val != null) {
                        Map<String, Object> t = new LinkedHashMap<>();
                        t.put("date", pDate);
                        t.put("total", val.longValue());
                        trend.add(t);
                    }
                    break;
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (String tr : rows(block)) {
            List<String> cs = cells(tr);
            if (cs.isEmpty()) {
                continue;
            }
            if (cs.get(0).equals("公告日期") && cs.size() > 1) {
                Matcher m = Pattern.compile("\\d{4}-\\d{2}-\\d{2}").matcher(cs.get(1));
                if (m.find()) {
                    announce = m.group(0);
                }
            } else if (cs.get(0).equals("股东总数") && cs.size() > 1) {
                total = num(cs.get(1));
            } else if (cs.size() >= 5 && cs.get(0).chars().allMatch(Character::isDigit)
                && !cs.get(0).isEmpty() && Integer.parseInt(cs.get(0)) <= 10) {
                // 列：编号/股东名称/持股数量(股)/持股比例(%)/股本性质，↑减持? ↑=增持 ↓=减持
                String note = cs.get(2).contains("↑") ? "增持" : (cs.get(2).contains("↓") ? "减持" : "");
                if ((cs.get(1) + cs.get(2)).contains("新进")) {
                    note = "新进";
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("rank", Integer.parseInt(cs.get(0)));
                row.put("name", cs.get(1));
                row.put("shares", num(cs.get(2)) != null ? num(cs.get(2)) : 0.0);
                row.put("ratio", num(cs.get(3)));
                row.put("note", note);
                out.add(row);
            }
        }
        if (out.isEmpty()) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", blockDate);
        result.put("announce", announce);
        result.put("total_holders", total);
        result.put("trend", trend.subList(0, Math.min(12, trend.size())));
        result.put("rows", out.subList(0, Math.min(10, out.size())));
        return result;
    }

    // ---------------------------------------------------------------- 三大报表

    /** 某年一页三大报表 -> {报告期日期: {科目: 值(万元)}}（科目顺序见 statements 合并逻辑） */
    private static Map<String, Map<String, Double>> statementPage(String kind, int year, String code) {
        String url = STATEMENT_URLS.get(kind).replace("{code}", code).replace("{year}", String.valueOf(year));
        String html = httpText(url);
        Map<String, Map<String, Double>> table = new LinkedHashMap<>();
        Matcher m = Pattern.compile("<table[^>]*id=\"\\w*NewTable0\".*?</table>", Pattern.DOTALL)
            .matcher(html);
        if (!m.find()) {
            return table;
        }
        List<String> dates = new ArrayList<>();
        for (String tr : rows(m.group())) {
            List<String> cs = cells(tr);
            if (cs.isEmpty()) {
                continue;
            }
            if (cs.get(0).equals("报表日期")) {
                dates = dateCells(cs);
                continue;
            }
            if (dates.isEmpty() || cs.get(0).isEmpty()) {
                continue; // 跳过表头单位行 / 空行
            }
            String label = cs.get(0);
            Map<String, Double> vals = table.computeIfAbsent(label, k -> new LinkedHashMap<>());
            for (int i = 0; i < dates.size(); i++) {
                vals.put(dates.get(i), i + 1 < cs.size() ? parseCell(cs.get(i + 1)) : null);
            }
        }
        return table;
    }

    /** 三大报表（利润表/资产负债表/现金流量表），最近 periods 个报告期，列从新到旧 */
    public static Map<String, Object> statements(String code, int periods) {
        int year = LocalDate.now().getYear();
        Map<String, Object> out = new LinkedHashMap<>();
        for (String kind : STATEMENT_URLS.keySet()) {
            // merged 为 {报告期: {科目: 值}}
            Map<String, Map<String, Double>> merged = new LinkedHashMap<>();
            for (int y : new int[]{year, year - 1}) {
                for (Map.Entry<String, Map<String, Double>> e : statementPage(kind, y, code).entrySet()) {
                    for (Map.Entry<String, Double> d : e.getValue().entrySet()) {
                        merged.computeIfAbsent(d.getKey(), k -> new LinkedHashMap<>())
                            .put(e.getKey(), d.getValue());
                    }
                }
            }
            if (merged.isEmpty()) {
                continue;
            }
            List<String> cols = merged.keySet().stream()
                .sorted(java.util.Comparator.reverseOrder())
                .limit(periods).toList();
            if (cols.isEmpty()) {
                continue;
            }
            // 科目按首见顺序（当年页在先）排列
            List<String> labels = new ArrayList<>();
            for (String d : cols) {
                for (String label : merged.getOrDefault(d, Map.<String, Double>of()).keySet()) {
                    if (!labels.contains(label)) {
                        labels.add(label);
                    }
                }
            }
            List<Map<String, Object>> rowsOut = new ArrayList<>();
            for (String label : labels) {
                List<Double> vals = new ArrayList<>();
                for (String d : cols) {
                    vals.add(merged.getOrDefault(d, Map.<String, Double>of()).get(label));
                }
                if (vals.stream().allMatch(java.util.Objects::isNull)) {
                    continue; // 无有效数据（跨年缺失科目）
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("label", label);
                row.put("vals", vals);
                rowsOut.add(row);
            }
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("title", STATEMENT_TITLES.get(kind));
            block.put("unit", "万元");
            block.put("periods", cols);
            block.put("rows", rowsOut);
            out.put(kind, block);
        }
        return out.isEmpty() ? null : out;
    }

    // ---------------------------------------------------------------- 公司概况

    private static final String[] PROFILE_FIELDS = {
        "公司名称", "公司英文名称", "上市市场", "上市日期", "发行价格", "成立日期",
        "注册资本", "董事会秘书", "注册地址", "办公地址", "公司网址", "公司电子邮箱",
    };

    /** 资料页一行内按 label/值 成对提取（label 以“：”结尾；先剥掉标签里的空白与 &nbsp;） */
    private static List<String[]> pairwiseCells(List<String> cs) {
        List<String[]> pairs = new ArrayList<>();
        int i = 0;
        while (i < cs.size() - 1) {
            String raw = cs.get(i).replace("&nbsp;", "").replace("\u00a0", "");
            String label = raw.replaceAll("[\\s：:]+$", "");
            String trimmed = raw.replaceAll("[\\s\\u00a0]+$", "");
            if (!label.isEmpty() && (trimmed.endsWith("：") || trimmed.endsWith(":"))) {
                String val = cs.get(i + 1).replace("&nbsp;", " ").replace("\u00a0", " ").trim();
                pairs.add(new String[]{label, val});
                i += 2;
            } else {
                i += 1;
            }
        }
        return pairs;
    }

    /** 公司概况（新浪 F10 公司资料页，仅 A 股） */
    public static Map<String, Object> profile(String kind, String code) {
        if (!"stock".equals(kind)) {
            return null;
        }
        String html = httpText(PROFILE_URL.replace("{code}", code));
        Map<String, String> info = new LinkedHashMap<>();
        for (String tr : rows(html)) {
            for (String[] pair : pairwiseCells(cells(tr))) {
                if (!pair[1].isEmpty() && !pair[1].equals("—") && !pair[1].equals("--")) {
                    info.put(pair[0], pair[1]);
                }
            }
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (String f : PROFILE_FIELDS) {
            if (info.get(f) != null && !info.get(f).isEmpty()) {
                fields.put(f, info.get(f));
            }
        }
        String intro = info.getOrDefault("公司简介", "");
        String mainbiz = info.getOrDefault("主营业务", "");
        if (fields.isEmpty() && intro.isEmpty() && mainbiz.isEmpty()) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fields", fields);
        result.put("intro", intro);
        result.put("mainbiz", mainbiz);
        return result;
    }

    // ---------------------------------------------------------------- 所属板块

    /** 申万行业归属 + 概念板块标签 + 同行业个股（新浪 F10，仅 A 股） */
    public static Map<String, Object> sectors(String kind, String code) {
        if (!"stock".equals(kind)) {
            return null;
        }
        String html = httpText(SECTORS_URL.replace("{code}", code));
        String industry = "";
        String typeId = "";
        Matcher segM = Pattern.compile("所属行业板块(.*?)所属概念板块", Pattern.DOTALL).matcher(html);
        if (segM.find()) {
            String seg = segM.group(1);
            Matcher mi = Pattern.compile("<td class=\"ct\"[^>]*>([^<]{2,12})</td>").matcher(seg);
            if (mi.find()) {
                industry = mi.group(1).trim();
            }
            Matcher mt = Pattern.compile("Type=(\\d+)").matcher(seg);
            if (mt.find()) {
                typeId = mt.group(1);
            }
        }
        // 概念标签：概念名 td 紧跟 mkt/# 链接，去重保序
        List<String> concepts = new ArrayList<>();
        Matcher cm = Pattern.compile(
                "<td class=\"ct\"[^>]*>([^<]{2,12})</td>\\s*<td[^>]*>\\s*<a[^>]*href=\"[^\"]*mkt/#(?:gn|chgn)",
                Pattern.DOTALL).matcher(html);
        while (cm.find()) {
            String name = cm.group(1).trim();
            if (!name.isEmpty() && !name.equals(industry) && !concepts.contains(name)) {
                concepts.add(name);
            }
        }
        if (industry.isEmpty() && concepts.isEmpty()) {
            return null;
        }

        List<Map<String, Object>> siblings = new ArrayList<>();
        if (!typeId.isEmpty()) {
            try {
                String sibHtml = httpText(SIBLING_URL
                    .replace("{code}", code).replace("{type_id}", typeId));
                for (String tr : rows(sibHtml)) {
                    List<String> cs = cells(tr);
                    if (cs.size() < 4 || !cs.get(1).contains("行情、资讯")) {
                        continue;
                    }
                    Matcher mc = Pattern.compile("(\\d{6})").matcher(cs.get(3));
                    if (!mc.find()) {
                        continue;
                    }
                    String scode = mc.group(1);
                    String zcode = code.length() >= 6 ? code.substring(code.length() - 6) : code;
                    if (scode.equals(zcode) || !(scode.startsWith("60") || scode.startsWith("00")
                        || scode.startsWith("30") || scode.startsWith("68"))) {
                        continue; // 排除自身与 B 股
                    }
                    String name = cs.get(1).split("\\(")[0].trim();
                    boolean dup = false;
                    for (Map<String, Object> s : siblings) {
                        if (s.get("code").equals(scode)) {
                            dup = true;
                            break;
                        }
                    }
                    if (!name.isEmpty() && !dup) {
                        Map<String, Object> s = new LinkedHashMap<>();
                        s.put("code", scode);
                        s.put("name", name);
                        siblings.add(s);
                    }
                }
            } catch (Exception ignored) {
                siblings = new ArrayList<>();
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("industry", industry);
        result.put("concepts", concepts.subList(0, Math.min(14, concepts.size())));
        result.put("siblings", siblings.subList(0, Math.min(10, siblings.size())));
        return result;
    }

    // ---------------------------------------------------------------- 融资融券（交易所官方直连）

    private static final String SSE_MARGIN_URL = "https://query.sse.com.cn/marketdata/tradedata/queryMargin.do";
    private static final String SZSE_MARGIN_URL = "https://www.szse.cn/api/report/ShowReport";

    /** 上交所某日全市场融资融券明细 -> {代码: 归一化字段}（query.sse.com.cn 偶发超时，重试 2 次） */
    private static Map<String, Map<String, Object>> sseDay(String day) {
        return cachedDay(SSE_CACHE, day, d -> {
            String date = d.replace("-", "");
            JsonNode result = null;
            IllegalStateException lastErr = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    String url = SSE_MARGIN_URL
                        + "?isPagination=true&tabType=mxtype&detailsDate=" + date
                        + "&stockCode=&beginDate=&endDate="
                        + "&pageHelp.pageSize=5000&pageHelp.pageCount=50&pageHelp.pageNo=1"
                        + "&pageHelp.beginPage=1&pageHelp.cacheSize=1&pageHelp.endPage=21";
                    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(TIMEOUT))
                        .header("Referer", "https://www.sse.com.cn/")
                        .header("User-Agent", "Mozilla/5.0")
                        .GET().build();
                    HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() != 200) {
                        throw new IllegalStateException("HTTP " + resp.statusCode());
                    }
                    result = MarketUtils.MAPPER.readTree(resp.body()).path("result");
                    break;
                } catch (java.io.IOException e) {
                    lastErr = new IllegalStateException(e.getMessage(), e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                if (attempt < 2) {
                    sleepQuietly(1000);
                }
            }
            if (result == null) {
                throw lastErr != null ? lastErr : new IllegalStateException("上交所请求失败");
            }
            // 字段：opDate/rqchl融券偿还/rqmcl融券卖出/rqyl融券余量/rzche融资偿还/rzmre融资买入/rzye融资余额
            Map<String, Map<String, Object>> out = new LinkedHashMap<>();
            for (JsonNode row : result) {
                String code = row.path("stockCode").asText("").trim();
                if (!code.isEmpty()) {
                    out.put(zfill(code), marginRow(
                        asNum(row.path("rzye")), asNum(row.path("rzmre")),
                        asNum(row.path("rzche")), asNum(row.path("rqyl")),
                        asNum(row.path("rqmcl"))));
                }
            }
            if (out.isEmpty()) {
                throw new IllegalStateException("上交所 " + d + " 无融资融券数据（非交易日）");
            }
            return out;
        });
    }

    /** 深交所某日全市场融资融券明细 -> {代码: 归一化字段}（官方 xlsx 报表，无偿还额字段） */
    private static Map<String, Map<String, Object>> szseDay(String day) {
        return cachedDay(SZSE_CACHE, day, d -> {
            String url = SZSE_MARGIN_URL
                + "?SHOWTYPE=xlsx&CATALOGID=1837_xxpl&txtDate=" + d
                + "&tab2PAGENO=1&random=0.123456&TABKEY=tab2";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Referer", "https://www.szse.cn/disclosure/margin/margin/index.html")
                .header("User-Agent", "Mozilla/5.0")
                .GET().build();
            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + resp.statusCode());
            }
            List<List<String>> table = parseXlsxTable(resp.body());
            // 列：0 证券代码 / 2 融资买入额 / 3 融资余额 / 4 融券卖出量 / 5 融券余量（无偿还额字段）
            Map<String, Map<String, Object>> out = new LinkedHashMap<>();
            for (int r = 1; r < table.size(); r++) { // 跳过表头行
                List<String> row = table.get(r);
                String code = row.size() > 0 ? row.get(0).trim() : "";
                if (code.matches("\\d{6}")) {
                    out.put(zfill(code), marginRow(
                        colNum(row, 3), colNum(row, 2), null, colNum(row, 5), colNum(row, 4)));
                }
            }
            if (out.isEmpty()) {
                throw new IllegalStateException("深交所 " + d + " 无融资融券数据（非交易日）");
            }
            return out;
        });
    }

    private static Double colNum(List<String> row, int idx) {
        return idx < row.size() ? num(row.get(idx)) : null;
    }

    private static Map<String, Object> marginRow(Double balance, Double buy, Double repay,
        Double shortVol, Double shortSell) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("balance", balance);
        row.put("buy", buy);
        row.put("repay", repay);
        row.put("short_vol", shortVol);
        row.put("short_sell", shortSell);
        return row;
    }

    private static Double asNum(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return Double.parseDouble(node.asText().replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 解析 xlsx 首个 worksheet 为字符串表格（zip + XML DOM，无 POI 依赖） */
    private static List<List<String>> parseXlsxTable(byte[] xlsx) throws Exception {
        Map<Integer, String> sharedStrings = new HashMap<>();
        byte[] sheet1 = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("xl/sharedStrings.xml")) {
                    sharedStrings.putAll(parseSharedStrings(zis.readAllBytes()));
                } else if (entry.getName().startsWith("xl/worksheets/sheet")
                    && entry.getName().endsWith(".xml") && sheet1 == null) {
                    sheet1 = zis.readAllBytes();
                }
                zis.closeEntry();
            }
        }
        List<List<String>> table = new ArrayList<>();
        if (sheet1 == null) {
            return table;
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Element root = factory.newDocumentBuilder()
            .parse(new ByteArrayInputStream(sheet1)).getDocumentElement();
        NodeList rowsEl = root.getElementsByTagNameNS("*", "row");
        for (int i = 0; i < rowsEl.getLength(); i++) {
            List<String> cellsOut = new ArrayList<>();
            NodeList cs = ((Element) rowsEl.item(i)).getElementsByTagNameNS("*", "c");
            for (int j = 0; j < cs.getLength(); j++) {
                cellsOut.add(xlsxCellValue((Element) cs.item(j), sharedStrings));
            }
            table.add(cellsOut);
        }
        return table;
    }

    private static Map<Integer, String> parseSharedStrings(byte[] xml) throws Exception {
        Map<Integer, String> out = new HashMap<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
        NodeList siList = root.getElementsByTagNameNS("*", "si");
        for (int i = 0; i < siList.getLength(); i++) {
            StringBuilder sb = new StringBuilder();
            NodeList ts = ((Element) siList.item(i)).getElementsByTagNameNS("*", "t");
            for (int j = 0; j < ts.getLength(); j++) {
                sb.append(ts.item(j).getTextContent());
            }
            out.put(i, sb.toString());
        }
        return out;
    }

    private static String xlsxCellValue(Element cell, Map<Integer, String> sharedStrings) {
        NodeList values = cell.getElementsByTagNameNS("*", "v");
        if (values.getLength() == 0) {
            return "";
        }
        String text = values.item(0).getTextContent();
        if ("s".equals(cell.getAttribute("t")) && !text.isEmpty()) {
            return sharedStrings.getOrDefault(Integer.parseInt(text.trim()), "");
        }
        return text;
    }

    /** 从 start 向前找最近一个有该代码数据（即有数据的）交易日 */
    private static Map.Entry<String, Map<String, Object>> findRecent(
        DayFetcher fetch, LocalDate start, String code, int maxBack) {
        for (int i = 0; i < maxBack; i++) {
            String day = start.minusDays(i).toString();
            try {
                Map<String, Object> hit = fetch.fetch(day).get(code);
                if (hit != null) {
                    return Map.entry(day, hit);
                }
            } catch (Exception ignored) {
                // 非交易日 / 网络失败，继续回溯
            }
        }
        return null;
    }

    private static String zfill(String code) {
        while (code.length() < 6) {
            code = "0" + code;
        }
        return code;
    }

    /** 最新交易日融资融券 + 上一交易日对比（沪深交易所官方数据） */
    public static Map<String, Object> margin(String kind, String code) {
        if (!"stock".equals(kind)) {
            return null;
        }
        String c = zfill(code);
        DayFetcher fetch = c.startsWith("6") || c.startsWith("5") || c.startsWith("9")
            ? F10Service::sseDay : F10Service::szseDay;

        Map.Entry<String, Map<String, Object>> latest = findRecent(fetch, LocalDate.now(), c, 10);
        if (latest == null) {
            return null;
        }
        String day = latest.getKey();
        Map<String, Object> cur = latest.getValue();
        Map.Entry<String, Map<String, Object>> prev =
            findRecent(fetch, LocalDate.parse(day).minusDays(1), c, 10);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", day);
        result.put("prev_date", prev != null ? prev.getKey() : null);
        result.put("balance", cur.get("balance"));
        Double delta = null;
        if (prev != null && cur.get("balance") instanceof Number b1
            && prev.getValue().get("balance") instanceof Number b0) {
            delta = round2(b1.doubleValue() - b0.doubleValue());
        }
        result.put("delta", delta);
        result.put("buy", cur.get("buy"));
        result.put("repay", cur.get("repay"));
        result.put("short_vol", cur.get("short_vol"));
        result.put("short_sell", cur.get("short_sell"));
        return result;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
