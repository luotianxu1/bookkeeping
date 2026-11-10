package com.example.market.fund;

import com.example.common.util.KeyedTtlCache;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;

/** 净值型公募基金排行及目录搜索。上游脚本只解析数据，不执行 JavaScript。 */
@Service
public class FundMarketService {
    private static final Set<String> TYPES = Set.of("all", "gp", "hh", "zq", "zs", "qdii", "fof");
    private static final Map<String, String> SORTS = Map.of("day", "rzdf", "week", "zzf", "month", "1yzf",
        "quarter", "3yzf", "halfYear", "6yzf", "year", "1nzf", "ytd", "jnzf");
    private static final Map<String, String> HEADERS = Map.of("User-Agent", "Mozilla/5.0", "Referer", "https://fund.eastmoney.com/");
    private final ObjectMapper json;
    private final Function<String, String> fetch;
    private final KeyedTtlCache<String, Ranking> rankings = new KeyedTtlCache<>(300_000, 15_000, 128);
    private final KeyedTtlCache<String, List<Fund>> catalog = new KeyedTtlCache<>(21_600_000, 15_000, 1);

    public FundMarketService() {
        this(url -> FundHttp.get(url, StandardCharsets.UTF_8, 15, HEADERS));
    }
    FundMarketService(Function<String, String> fetch) {
        this.fetch = fetch;
        this.json = new ObjectMapper().enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public record Row(String code, String name, String navDate, BigDecimal nav, BigDecimal accumulatedNav,
        BigDecimal day, BigDecimal week, BigDecimal month, BigDecimal quarter, BigDecimal halfYear,
        BigDecimal year, BigDecimal ytd) {}
    public record Ranking(List<Row> items, int total, int page, int pageSize, String fetchedAt, String source) {}
    public record Fund(String code, String name, String type, String initials, String pinyin) {}
    public record SearchResult(List<Fund> items, int total) {}

    public Ranking list(String type, String sort, String order, int page, int pageSize) {
        if (!TYPES.contains(type) || !SORTS.containsKey(sort) || !Set.of("asc", "desc").contains(order))
            throw new IllegalArgumentException("基金筛选或排序参数无效");
        if (page < 1 || page > 2000 || pageSize < 1 || pageSize > 50) throw new IllegalArgumentException("基金分页参数无效");
        String url = "https://fund.eastmoney.com/data/rankhandler.aspx?op=ph&dt=kf&rs=&gs=0&dx=1&ft=" + type
            + "&sc=" + SORTS.get(sort) + "&st=" + order + "&pi=" + page + "&pn=" + pageSize;
        Ranking result = rankings.get(url, key -> parseRanking(fetch.apply(key), page, pageSize));
        if (result == null) throw new IllegalStateException("基金行情暂时无法获取，请稍后重试");
        return result;
    }

    public SearchResult search(String keyword) {
        String query = keyword == null ? "" : keyword.trim().toUpperCase(Locale.ROOT);
        if (query.length() > 60) throw new IllegalArgumentException("基金搜索关键词不能超过60字");
        if (query.isEmpty()) return new SearchResult(List.of(), 0);
        List<Fund> funds = catalog.get("catalog", key -> parseCatalog(fetch.apply("https://fund.eastmoney.com/js/fundcode_search.js")));
        if (funds == null) throw new IllegalStateException("基金搜索暂时不可用，请稍后重试");
        List<Fund> matches = funds.stream().filter(f -> score(f, query) > 0)
            .sorted(Comparator.<Fund>comparingInt(f -> score(f, query)).reversed().thenComparing(Fund::code)).toList();
        return new SearchResult(matches.stream().limit(30).toList(), matches.size());
    }
    private int score(Fund f, String q) {
        if (f.code().equals(q)) return 5;
        if (f.name().toUpperCase(Locale.ROOT).equals(q)) return 4;
        if (f.code().startsWith(q)) return 3;
        if (f.name().toUpperCase(Locale.ROOT).contains(q)) return 2;
        return f.initials().contains(q) || f.pinyin().contains(q) ? 1 : 0;
    }

    Ranking parseRanking(String script, int page, int pageSize) {
        try {
            JsonNode root = json.readTree(assignment(script));
            if (!root.path("datas").isArray() || !root.path("allRecords").canConvertToInt()
                || root.path("allRecords").asInt() < 0 || root.path("pageIndex").asInt() != page)
                throw new IllegalArgumentException("Unexpected ranking response");
            List<Row> rows = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (JsonNode item : root.path("datas")) {
                String[] f = item.asText().split(",", -1);
                if (f.length < 15 || !f[0].matches("[0-9]{6}") || f[1].isBlank()) throw new IllegalArgumentException("Invalid fund row");
                if (!seen.add(f[0])) continue;
                rows.add(new Row(f[0], f[1], LocalDate.parse(f[3]).toString(), number(f[4]), number(f[5]),
                    number(f[6]), number(f[7]), number(f[8]), number(f[9]), number(f[10]), number(f[11]), number(f[14])));
            }
            int total = root.path("allRecords").asInt();
            if (rows.size() > pageSize || (rows.isEmpty() && (long)(page - 1) * pageSize < total))
                throw new IllegalArgumentException("Incomplete ranking response");
            return new Ranking(List.copyOf(rows), total, page, pageSize,
                OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString(), "天天基金");
        } catch (Exception e) { throw new IllegalStateException("基金行情数据格式异常", e); }
    }
    List<Fund> parseCatalog(String script) {
        try {
            JsonNode root = json.readTree(assignment(script));
            if (!root.isArray() || root.isEmpty()) throw new IllegalArgumentException("Empty fund catalog");
            Map<String, Fund> funds = new LinkedHashMap<>();
            for (JsonNode row : root) {
                if (!row.isArray() || row.size() < 5 || !row.get(0).asText().matches("[0-9]{6}")) continue;
                String type = row.get(3).asText();
                // 排行为净值型基金；货币/短期理财使用另一套收益口径，不混入净值列表。
                if (type.contains("货币") || type.contains("理财")) continue;
                Fund f = new Fund(row.get(0).asText(), row.get(2).asText(), type,
                    row.get(1).asText().toUpperCase(Locale.ROOT), row.get(4).asText().toUpperCase(Locale.ROOT));
                funds.putIfAbsent(f.code(), f);
            }
            if (funds.isEmpty()) throw new IllegalArgumentException("Invalid fund catalog");
            return List.copyOf(funds.values());
        } catch (Exception e) { throw new IllegalStateException("基金目录数据格式异常", e); }
    }
    private String assignment(String script) {
        String value = Objects.requireNonNull(script).strip().replaceFirst("^\\uFEFF", "");
        int equals = value.indexOf('=');
        if (!value.startsWith("var ") || equals < 0) throw new IllegalArgumentException("Invalid fund data");
        value = value.substring(equals + 1).trim();
        if (value.endsWith(";")) value = value.substring(0, value.length() - 1);
        // FAIL_ON_TRAILING_TOKENS also rejects executable text after the data object.
        return value;
    }
    private BigDecimal number(String value) {
        if (value.isBlank() || value.equals("--") || value.equals("---")) return null;
        return new BigDecimal(value);
    }
}
