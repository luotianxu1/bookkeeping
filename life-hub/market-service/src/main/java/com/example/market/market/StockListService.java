package com.example.market.market;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 全市场 A 股清单（新浪行情中心 hs_a 节点：沪深主板/创业板/科创板/北交所，约 5500+ 只）。
 *
 * <p>供选股引擎等调用方枚举全市场标的：code/name/symbol/market 四字段，
 * 停牌或无效标的（最新价 trade <= 0）被过滤。清单每个交易日收盘后才变化，
 * 内存缓存 30 分钟避免高频打源。</p>
 */
public final class StockListService {

    private static final String PAGE_URL = "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php"
        + "/Market_Center.getHQNodeData?page=%d&num=100&sort=symbol&asc=1&node=hs_a";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 80;
    private static final long CACHE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(30);

    private static volatile List<Map<String, Object>> cache = List.of();
    private static volatile long cacheLoadedAt = 0;

    private StockListService() {
    }

    /** 全市场有效 A 股清单（带 30 分钟缓存）。行字段：code/name/symbol/market */
    public static List<Map<String, Object>> aShareUniverse() {
        List<Map<String, Object>> snapshot = cache;
        if (!snapshot.isEmpty() && System.currentTimeMillis() - cacheLoadedAt < CACHE_TTL_MILLIS) {
            return snapshot;
        }
        synchronized (StockListService.class) {
            snapshot = cache;
            if (!snapshot.isEmpty() && System.currentTimeMillis() - cacheLoadedAt < CACHE_TTL_MILLIS) {
                return snapshot;
            }
            try {
                List<Map<String, Object>> loaded = fetchUniverse();
                cache = loaded;
                cacheLoadedAt = System.currentTimeMillis();
                return loaded;
            } catch (Exception e) {
                // 刷新失败时回退旧缓存（宁旧勿缺），完全没有缓存才向上抛
                if (!cache.isEmpty()) {
                    return cache;
                }
                throw e;
            }
        }
    }

    private static List<Map<String, Object>> fetchUniverse() {
        Map<String, Map<String, Object>> stocks = new LinkedHashMap<>();
        boolean sawSz = false;
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode rows = fetchPageWithRetry(page);
            if (!rows.isArray() || rows.isEmpty()) {
                break;
            }
            for (JsonNode row : rows) {
                String symbol = row.path("symbol").asText("").trim();
                String code = row.path("code").asText("").trim();
                String name = row.path("name").asText("").trim();
                double trade = row.path("trade").asDouble(0);
                if (code.isBlank() || name.isBlank() || trade <= 0) {
                    continue;
                }
                String market = symbol.startsWith("sh") ? "SH" : symbol.startsWith("sz") ? "SZ"
                    : symbol.startsWith("bj") ? "BJ" : null;
                if (market == null || stocks.containsKey(code)) {
                    continue;
                }
                if ("SZ".equals(market)) {
                    sawSz = true;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", code);
                item.put("name", name);
                item.put("symbol", market.toLowerCase(java.util.Locale.ROOT) + code);
                item.put("market", market);
                stocks.put(code, item);
            }
            if (rows.size() < PAGE_SIZE) {
                break;
            }
            try {
                // 分页限速，避免触发新浪频控
                TimeUnit.MILLISECONDS.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // 完整性守卫：深市缺失说明分页中途被限流，宁可失败也不返回半份清单
        if (!sawSz) {
            throw new IllegalStateException("新浪A股清单分页不完整（缺少深市数据）");
        }
        return new ArrayList<>(stocks.values());
    }

    /** 单页抓取，最多 3 次重试（指数退避）；重试仍失败直接抛错，绝不静默返回部分数据 */
    private static JsonNode fetchPageWithRetry(int page) {
        IllegalStateException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String body = TencentClient.httpGetJson(String.format(PAGE_URL, page));
                JsonNode rows = MarketUtils.MAPPER.readTree(body);
                if (rows.isArray() && !rows.isEmpty()) {
                    return rows;
                }
                // 首页为空视为真无数据；后续页空可能是限流，按失败重试
                if (page == 1) {
                    return rows;
                }
                last = new IllegalStateException("新浪A股清单第 " + page + " 页返回空");
            } catch (Exception e) {
                last = e instanceof IllegalStateException ise ? ise
                    : new IllegalStateException("新浪A股清单第 " + page + " 页抓取失败", e);
            }
            try {
                TimeUnit.MILLISECONDS.sleep(500L * attempt);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw last != null ? last : new IllegalStateException("清单抓取被中断");
            }
        }
        throw last != null ? last : new IllegalStateException("新浪A股清单第 " + page + " 页抓取失败");
    }
}
