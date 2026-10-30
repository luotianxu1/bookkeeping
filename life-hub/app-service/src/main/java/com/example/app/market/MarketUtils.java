package com.example.app.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 行情模块共享工具：JSON 解析、K 线行构造、简单 TTL 缓存。
 */
public final class MarketUtils {

    private MarketUtils() {
    }

    public static final ObjectMapper MAPPER = new ObjectMapper();

    /** 带 TTL 的懒加载缓存（线程安全，失败不缓存） */
    public static final class TtlCache<V> {
        private final long ttlMillis;
        private final ReentrantLock lock = new ReentrantLock();
        private V value;
        private long loadedAt;

        public TtlCache(long ttlMillis) {
            this.ttlMillis = ttlMillis;
        }

        public V get(Supplier<V> loader) {
            lock.lock();
            try {
                long now = System.currentTimeMillis();
                if (value != null && now - loadedAt < ttlMillis) {
                    return value;
                }
                value = loader.get();
                loadedAt = now;
                return value;
            } finally {
                lock.unlock();
            }
        }

        /** 是否已有有效缓存 */
        public boolean isFresh() {
            lock.lock();
            try {
                return value != null && System.currentTimeMillis() - loadedAt < ttlMillis;
            } finally {
                lock.unlock();
            }
        }
    }

    /** 构造统一 K 线行：日期/开盘/最高/最低/收盘/成交量/涨跌幅（相对前一根收盘） */
    public static Map<String, Object> klineRow(String date, double open, double high, double low,
                                               double close, long volume, Double prevClose) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("日期", date);
        row.put("开盘", open);
        row.put("最高", high);
        row.put("最低", low);
        row.put("收盘", close);
        row.put("成交量", volume);
        row.put("涨跌幅", prevClose != null && prevClose != 0
            ? Math.round((close - prevClose) / prevClose * 10000) / 100.0
            : null);
        return row;
    }

    /** 今日 ISO 日期（yyyy-MM-dd） */
    public static String today() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /** ISO 日期偏移 N 天 */
    public static String dateOffset(String isoDate, int days) {
        return LocalDate.parse(isoDate).plusDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /** 解析 JSONP 括号内载荷：`(...)` → JsonNode */
    public static JsonNode parseJsonp(String text) {
        try {
            int start = text.indexOf('(');
            int end = text.lastIndexOf(')');
            if (start < 0 || end <= start) {
                return MAPPER.nullNode();
            }
            return MAPPER.readTree(text.substring(start + 1, end));
        } catch (Exception e) {
            return MAPPER.nullNode();
        }
    }

    public static Double parseDouble(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        return parseDouble(node.asText());
    }

    /** 解析数字文本：去逗号/空白，失败返回 null */
    public static Double parseDouble(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static long parseLongSafe(String value) {
        try {
            return (long) Double.parseDouble(value.trim().replace(",", ""));
        } catch (Exception e) {
            return 0L;
        }
    }
}
