package com.example.market.service;

import com.example.market.market.MarketUtils;
import com.example.market.market.TencentClient;
import com.example.market.market.TrendService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 美股行情页聚合接口（2026-10-30 自前端东财 JSONP 直连收编而来）。
 *
 * <p>页面两张指数卡统一由此供给：标普500 走腾讯 usINX 快照 + SPY ETF 分时
 * 按昨收比折算（复用 TrendService.trendUs）；纳斯达克100 无公开指数分钟源，
 * 用 QQQ 代理（腾讯 usQQQ 快照 + 纳斯达克官方 QQQ 分时，与页面代理卡口径一致）。
 * 15s 内存缓存，失败回吐旧缓存，避免页面轮询打透上游。</p>
 */
@Service
public class UsOverviewService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long CACHE_MILLIS = 15_000L;

    /**
     * @param code          页面卡片代码
     * @param displayName   展示名
     * @param tencentSymbol 腾讯快照代码
     * @param trendSymbol   分时代码（null 表示走 TrendService.trendUs 的 ETF 折算）
     * @param assetClass    纳斯达克官方 chart 的 assetclass
     * @param proxySymbol   代理标注（null 表示非代理）
     */
    private record IndexDef(
        String code,
        String displayName,
        String tencentSymbol,
        String trendSymbol,
        String assetClass,
        String proxySymbol,
        String proxyName
    ) {
    }

    private static final List<IndexDef> INDEX_DEFS = List.of(
        new IndexDef("sp500", "标普500", "usINX", null, null, null, null),
        new IndexDef("ndx100", "纳斯达克100", "usQQQ", "QQQ", "etf", "QQQ", "Invesco QQQ ETF")
    );

    private final AtomicReference<CachedOverview> cache = new AtomicReference<>();

    public Map<String, Object> getOverview() {
        CachedOverview cached = cache.get();
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.cachedAt() < CACHE_MILLIS) {
            return cached.payload();
        }
        try {
            Map<String, Object> payload = fetchOverview();
            cache.set(new CachedOverview(payload, now));
            return payload;
        } catch (Exception ex) {
            if (cached != null) {
                return cached.payload();
            }
            throw new IllegalStateException("美股行情加载失败", ex);
        }
    }

    private Map<String, Object> fetchOverview() {
        List<String> symbols = INDEX_DEFS.stream().map(IndexDef::tencentSymbol).toList();
        Map<String, List<String>> qt = TencentClient.tencentQt(symbols);

        List<Map<String, Object>> indices = new ArrayList<>();
        for (IndexDef def : INDEX_DEFS) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", def.code());
            item.put("name", def.displayName());
            if (def.proxySymbol() != null) {
                item.put("proxySymbol", def.proxySymbol());
                item.put("proxyName", def.proxyName());
            }
            appendQuote(item, def, qt.get(def.tencentSymbol()));
            appendTrend(item, def);
            indices.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("updatedAt", LocalDateTime.now(MARKET_ZONE).toString());
        payload.put("indices", indices);
        return payload;
    }

    /** 腾讯快照字段：f1名称 f3最新 f4昨收 f5今开 f30时间 f31涨跌额 f32涨跌幅 f33最高 f34最低 */
    private void appendQuote(Map<String, Object> item, IndexDef def, List<String> f) {
        if (f == null || f.size() <= 34) {
            return;
        }
        item.put("name", def.displayName());
        item.put("price", MarketUtils.parseDouble(f.get(3)));
        item.put("preClose", MarketUtils.parseDouble(f.get(4)));
        item.put("open", MarketUtils.parseDouble(f.get(5)));
        item.put("change", MarketUtils.parseDouble(f.size() > 31 ? f.get(31) : ""));
        item.put("changePercent", MarketUtils.parseDouble(f.size() > 32 ? f.get(32) : ""));
        item.put("high", MarketUtils.parseDouble(f.get(33)));
        item.put("low", MarketUtils.parseDouble(f.get(34)));
        item.put("quoteTime", f.get(30));
    }

    private void appendTrend(Map<String, Object> item, IndexDef def) {
        Map<String, Object> trend = def.trendSymbol() != null
            ? TrendService.nasdaqMinute(def.trendSymbol(), def.assetClass(), def.displayName())
            : TrendService.trendUs(def.code());
        if (trend == null) {
            item.put("points", List.of());
            return;
        }
        Object points = trend.get("points");
        item.put("points", points instanceof List ? points : List.of());
        item.put("trendDate", trend.get("date"));
        item.put("trendPrevClose", trend.get("prev_close"));
    }

    private record CachedOverview(Map<String, Object> payload, long cachedAt) {
    }
}
