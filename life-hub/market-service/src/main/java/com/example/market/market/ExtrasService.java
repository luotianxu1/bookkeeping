package com.example.market.market;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 详情页扩展信息：资金流向 / 公司公告 / 分红送配（对应 investment 项目 services/extras.py）。
 *
 * <p>数据源均为新浪（仅 A 股标的有此类数据），带 64 条 LRU 式进程缓存。</p>
 */
public final class ExtrasService {

    private ExtrasService() {
    }

    private static final int CACHE_MAX = 64;
    private static final Map<String, List<Map<String, Object>>> ANNOUNCEMENT_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<Map<String, Object>>> DIVIDEND_CACHE = new ConcurrentHashMap<>();

    private static final Pattern BULLETIN_RE = Pattern.compile(
        "<a target='_blank' href='(/corp/view/vCB_AllBulletinDetail\\.php\\?stockid=\\d+&(?:amp;)?id=\\d+)'>"
            + "([^<]{4,120})</a>");
    private static final Pattern DATE_RE = Pattern.compile("20\\d{2}-\\d{2}-\\d{2}");
    private static final Pattern ROW_CELLS_RE = Pattern.compile("<t[dh][^>]*>(.*?)</t[dh]>", Pattern.DOTALL);
    private static final Pattern TAGS_RE = Pattern.compile("<[^>]*>");

    /** 近 20 日资金流向（新浪 MoneyFlow），按日期倒序 */
    public static List<Map<String, Object>> moneyflow(String kind, String code) {
        if (!kind.equals("stock")) {
            return List.of();
        }
        String sym = KlineService.sinaSymbol(kind, code);
        String url = "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php"
            + "/MoneyFlow.ssl_qsfx_lscjfb?page=1&num=20&sort=opendate&asc=0&daima=" + sym;
        JsonNode raw;
        try {
            raw = MarketUtils.MAPPER.readTree(SinaClient.getText(url, StandardCharsets.UTF_8, 10));
        } catch (Exception e) {
            return List.of();
        }
        if (!raw.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode it : raw) {
            try {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("date", it.path("opendate").asText());
                row.put("close", it.path("trade").asDouble());
                row.put("pct", round2(it.path("changeratio").asDouble() * 100));
                row.put("turnover", round2(it.path("turnover").asDouble()));
                row.put("ratio", round2(it.path("ratioamount").asDouble() * 100));
                row.put("net", it.path("netamount").asDouble());
                row.put("xl_net", it.path("r0_net").asDouble());
                row.put("l_net", it.path("r1_net").asDouble());
                row.put("m_net", it.path("r2_net").asDouble());
                row.put("s_net", it.path("r3_net").asDouble());
                rows.add(row);
            } catch (Exception ignored) {
                // 跳过异常行
            }
        }
        return rows;
    }

    /** 最新公告列表（标题/日期/新浪原文链接），按页面顺序取前 12 条 */
    public static List<Map<String, Object>> announcements(String kind, String code) {
        if (!kind.equals("stock")) {
            return List.of();
        }
        String cacheKey = code;
        List<Map<String, Object>> cached = ANNOUNCEMENT_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String sym = KlineService.sinaSymbol(kind, code);
        String page = SinaClient.getText(
            "https://vip.stock.finance.sina.com.cn/corp/go.php/vCB_AllBulletin/stockid/"
                + sym.substring(2) + ".phtml");
        // 页面结构：日期行在前，其后若干条目归属该日期；按位置顺序游标归组
        List<Object[]> events = new ArrayList<>(); // [position, type, value]
        Matcher dateMatcher = DATE_RE.matcher(page);
        while (dateMatcher.find()) {
            events.add(new Object[]{dateMatcher.start(), "date", dateMatcher.group()});
        }
        Matcher bulletinMatcher = BULLETIN_RE.matcher(page);
        while (bulletinMatcher.find()) {
            events.add(new Object[]{bulletinMatcher.start(), "item",
                new String[]{bulletinMatcher.group(1), unescape(bulletinMatcher.group(2)).trim()}});
        }
        events.sort(java.util.Comparator.comparingInt(e -> (Integer) e[0]));

        List<Map<String, Object>> out = new ArrayList<>();
        String curDate = "";
        for (Object[] event : events) {
            if (event[1].equals("date")) {
                curDate = (String) event[2];
            } else {
                String[] pair = (String[]) event[2];
                String href = pair[0];
                String title = pair[1];
                if (out.isEmpty() || !out.get(out.size() - 1).get("title").equals(title)) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("date", curDate);
                    item.put("title", title);
                    item.put("url", "https://vip.stock.finance.sina.com.cn" + href);
                    out.add(item);
                }
                if (out.size() >= 12) {
                    break;
                }
            }
        }
        if (ANNOUNCEMENT_CACHE.size() >= CACHE_MAX) {
            ANNOUNCEMENT_CACHE.clear();
        }
        ANNOUNCEMENT_CACHE.put(cacheKey, out);
        return out;
    }

    /** 分红送配记录（按公告日期倒序，取前 12 条） */
    public static List<Map<String, Object>> dividends(String kind, String code) {
        if (!kind.equals("stock")) {
            return List.of();
        }
        String cacheKey = code;
        List<Map<String, Object>> cached = DIVIDEND_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String sym = KlineService.sinaSymbol(kind, code);
        String page = SinaClient.getText(
            "https://vip.stock.finance.sina.com.cn/corp/go.php/vISSUE_ShareBonus/stockid/"
                + sym.substring(2) + ".phtml");

        String table = findBonusTable(page);
        if (table == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        Matcher trMatcher = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL).matcher(table);
        while (trMatcher.find() && out.size() < 12) {
            List<String> cells = cells(trMatcher.group(1));
            if (cells.size() < 7 || !cells.get(0).matches("20\\d{2}-\\d{2}-\\d{2}")) {
                continue; // 跳过表头/嵌套表头/空行
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("announce", cells.get(0));
            row.put("bonus", numOrZero(cells.get(1))); // 每10股送股
            row.put("convert", numOrZero(cells.get(2))); // 每10股转增
            row.put("dividend", numOrZero(cells.get(3))); // 每10股派息（税前，元）
            row.put("progress", cells.get(4));
            row.put("ex_date", cells.get(5).matches("20\\d{2}-\\d{2}-\\d{2}") ? cells.get(5) : null);
            row.put("record_date", cells.size() > 6 && cells.get(6).matches("20\\d{2}-\\d{2}-\\d{2}")
                ? cells.get(6) : null);
            out.add(row);
        }
        if (DIVIDEND_CACHE.size() >= CACHE_MAX) {
            DIVIDEND_CACHE.clear();
        }
        DIVIDEND_CACHE.put(cacheKey, out);
        return out;
    }

    /** 定位 ShareBonus 表（优先 id 匹配，兜底含「公告日期」+「派息」的表） */
    private static String findBonusTable(String page) {
        Matcher m = Pattern.compile("<table[^>]*id=\"ShareBonus[^\"]*\"[^>]*>(.*?)</table>", Pattern.DOTALL)
            .matcher(page);
        if (m.find()) {
            return m.group(1);
        }
        Matcher tables = Pattern.compile("<table[^>]*>(.*?)</table>", Pattern.DOTALL).matcher(page);
        while (tables.find()) {
            String body = tables.group(1);
            if (body.contains("公告日期") && body.contains("派息")) {
                return body;
            }
        }
        return null;
    }

    private static List<String> cells(String trHtml) {
        List<String> out = new ArrayList<>();
        Matcher m = ROW_CELLS_RE.matcher(trHtml);
        while (m.find()) {
            out.add(unescape(TAGS_RE.matcher(m.group(1)).replaceAll("")).trim());
        }
        return out;
    }

    private static String unescape(String text) {
        return text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
    }

    private static double numOrZero(String v) {
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
