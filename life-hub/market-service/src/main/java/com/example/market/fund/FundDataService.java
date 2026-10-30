package com.example.market.fund;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基金/报价聚合服务：为 app-service 投资模块提供统一数据通道，
 * 并为后续 market-service 基金功能提供底座。
 *
 * <p>数据源：新浪（估值/概况/费率/股票分红）+ howbuy（净值历史）+ 腾讯（搜索/A股快照）
 * + 东财（仅基金分红历史，独家数据）。对外字段与 app-service 原消费口径保持兼容。</p>
 */
@Service
public class FundDataService {

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public FundDataService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ---------- 对外 API ----------

    /** 基金基础信息（EM 兼容字段：SHORTNAME/FCODE/DWJZ/FSRQ/RZDF/LJJZ/SGZT/SHZT/FTYPE/JJGS） */
    public Map<String, Object> fundBase(String code) {
        return cached("base:" + code, 5 * 60_000L, () -> assembleFundBase(code));
    }

    /** 基金盘中估值（EM 兼容字段：fundcode/name/jzrq/dwjz/gsz/gztime） */
    public Map<String, Object> fundEstimate(String code) {
        return cached("est:" + code, 60_000L, () -> assembleFundEstimate(code));
    }

    /** 基金费率（赎回费分档 label 已归一为 app-service 可解析的措辞） */
    public Map<String, Object> fundFees(String code) {
        return cached("fee:" + code, 30 * 60_000L, () -> {
            Map<String, Object> fees = SinaFundClient.fundFees(code);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("matched", fees != null);
            out.putAll(fees != null ? fees : Map.of("redeemFeeRules", List.of()));
            return out;
        });
    }

    /** 基金净值历史（新浪官方开放接口，倒序；近4年窗口） */
    public Map<String, Object> fundNavHistory(String code) {
        List<Map<String, Object>> items = navItems(code);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("matched", items != null && !items.isEmpty());
        out.put("items", items == null ? List.of() : items);
        return out;
    }

    /** 产品搜索（腾讯 smartbox） */
    public Map<String, Object> search(String keyword) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", TencentQuoteClient.search(keyword));
        return out;
    }

    /** A股快照（名称/现价） */
    public Map<String, Object> stockSnapshot(String code) {
        return cached("snap:" + code, 15_000L, () -> TencentQuoteClient.stockSnapshot(code));
    }

    /** 股票分红历史（新浪，每10股口径） */
    public Map<String, Object> stockDividends(String code) {
        return cached("sdiv:" + code, 60 * 60_000L, () -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("matched", true);
            out.put("items", SinaFundClient.stockDividends(code));
            return out;
        });
    }

    /** 基金分红历史（东财，独家数据源） */
    public Map<String, Object> fundDividends(String code) {
        return cached("fdiv:" + code, 60 * 60_000L, () -> EastmoneyFundDividendClient.fetch(code));
    }

    // ---------- 组装 ----------

    private Map<String, Object> assembleFundBase(String code) {
        Map<String, Object> profile = safe(() -> SinaFundClient.fundProfile(code));
        Map<String, Object> fu = safe(() -> SinaFundClient.fuQuote(code));
        List<Map<String, Object>> nav = navItems(code);

        String shortName = str(profile, "shortName");
        if (shortName == null && fu != null) {
            shortName = str(fu, "name");
        }
        // 净值摘要取自净值历史头部（倒序第一行），日涨跌幅由最近两日计算
        String navPrice = firstNav(nav, "nav");
        String navDate = nav == null || nav.isEmpty() ? null : str(nav.get(0), "date");
        String changePercent = dailyChangePercent(nav);
        String accNav = firstNav(nav, "accNav");
        if (navPrice == null && fu != null) {
            navPrice = str(fu, "dwjz");
        }
        if (accNav == null && fu != null) {
            accNav = str(fu, "ljjz");
        }
        if (navDate == null && fu != null) {
            navDate = str(fu, "date");
        }
        if (changePercent == null && fu != null) {
            changePercent = str(fu, "changePercent");
        }

        Map<String, Object> datas = new LinkedHashMap<>();
        datas.put("SHORTNAME", shortName);
        datas.put("FCODE", code);
        datas.put("DWJZ", navPrice);
        datas.put("FSRQ", navDate);
        datas.put("RZDF", changePercent);
        datas.put("LJJZ", accNav);
        datas.put("SGZT", profile != null ? profile.get("purchaseStatus") : null);
        datas.put("SHZT", profile != null ? profile.get("redeemStatus") : null);
        datas.put("FTYPE", profile != null ? profile.get("fundType") : null);
        datas.put("JJGS", profile != null ? profile.get("company") : null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("matched", shortName != null || navPrice != null);
        out.put("Datas", datas);
        return out;
    }

    private Map<String, Object> assembleFundEstimate(String code) {
        Map<String, Object> fu = safe(() -> SinaFundClient.fuQuote(code));
        List<Map<String, Object>> nav = navItems(code);

        String navPrice = firstNav(nav, "nav");
        String navDate = nav == null || nav.isEmpty() ? null : str(nav.get(0), "date");
        String name = fu != null ? str(fu, "name") : null;
        if (navPrice == null && fu != null) {
            navPrice = str(fu, "dwjz");
        }
        if (navDate == null && fu != null) {
            navDate = str(fu, "date");
        }
        if (name == null) {
            Map<String, Object> profile = safe(() -> SinaFundClient.fundProfile(code));
            name = profile != null ? str(profile, "shortName") : null;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fundcode", code);
        out.put("name", name);
        out.put("jzrq", navDate);
        out.put("dwjz", navPrice);
        out.put("gsz", fu != null ? str(fu, "gsz") : null);
        if (fu != null) {
            String date = str(fu, "date");
            String time = str(fu, "time");
            out.put("gztime", date != null && time != null
                ? date + " " + (time.length() >= 5 ? time.substring(0, 5) : time)
                : null);
        } else {
            out.put("gztime", null);
        }
        out.put("matched", navPrice != null || out.get("gsz") != null);
        return out;
    }

    /** 净值历史容错获取（base/estimate/nav-history 共用一次缓存，缓存值为列表本身） */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> navItems(String code) {
        try {
            return cached("nav:" + code, 30 * 60_000L,
                () -> SinaNavHistoryClient.fetch(code, LocalDate.now().minusYears(4)));
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNav(List<Map<String, Object>> nav, String key) {
        if (nav == null || nav.isEmpty()) {
            return null;
        }
        return str(nav.get(0), key);
    }

    /** 净值日涨跌幅%：由最近两日单位净值计算，数据不足时返回 null */
    private static String dailyChangePercent(List<Map<String, Object>> nav) {
        if (nav == null || nav.size() < 2) {
            return null;
        }
        try {
            double latest = Double.parseDouble(str(nav.get(0), "nav"));
            double prev = Double.parseDouble(str(nav.get(1), "nav"));
            if (prev <= 0) {
                return null;
            }
            return String.format("%.4f", (latest - prev) / prev * 100);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- 缓存与工具 ----------

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, long ttlMs, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(key);
        if (entry != null && entry.expireAt > now) {
            return (T) entry.value;
        }
        T value;
        try {
            value = loader.get();
        } catch (Exception e) {
            throw new IllegalStateException("行情数据源请求失败: " + e.getMessage(), e);
        }
        cache.put(key, new CacheEntry(now + ttlMs, value));
        return value;
    }

    /** 上游部分失败不阻断整体组装：返回 null 而非抛错 */
    private <T> T safe(Supplier<T> loader) {
        try {
            return loader.get();
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map == null ? null : map.get(key);
        return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v);
    }

    private record CacheEntry(long expireAt, Object value) {
    }

    @FunctionalInterface
    private interface Supplier<T> {
        T get() throws Exception;
    }
}
