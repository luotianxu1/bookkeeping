package com.example.market.market;

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

    /**
     * 带 TTL + single-flight + 失败降级的键值缓存（线程安全）。
     *
     * <p>语义：</p>
     * <ul>
     *   <li>命中未过期条目直接返回；过期后回源，同一 key 的并发回源按 key 串行化
     *       （等待者在锁上拿到回源后的新值），不同 key 互不影响；</li>
     *   <li>回源成功 → 写入正常条目（TTL 内有效）；</li>
     *   <li>回源失败 → 有旧值时降级为 stale 条目（仅 failureTtl 窗口内有效，
     *       避免上游故障期间每请求都直打上游），无旧值时写入 null 负条目；
     *       窗口过后下一次 get 自动重试上游；</li>
     *   <li>get 返回 null 表示「上游失败且窗口内无可用值」，调用方自行兜底；
     *       loader 正常路径不得返回 null（用异常表达失败）。</li>
     * </ul>
     *
     * @param ttlMillis        成功回源的有效期
     * @param failureTtlMillis 失败降级条目（stale/null）的有效期，窗口过后重试
     * @param maxSize          容量上限；满时先清过期条目，仍满则整体清空（0 = 不限）
     */
    public static final class KeyedTtlCache<K, V> {
        private final long ttlMillis;
        private final long failureTtlMillis;
        private final int maxSize;
        private final java.util.concurrent.ConcurrentHashMap<K, Entry<V>> map = new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.concurrent.ConcurrentHashMap<K, Object> keyLocks = new java.util.concurrent.ConcurrentHashMap<>();

        private record Entry<V>(V value, long validUntil, boolean stale) {
        }

        public KeyedTtlCache(long ttlMillis, long failureTtlMillis, int maxSize) {
            this.ttlMillis = ttlMillis;
            this.failureTtlMillis = failureTtlMillis;
            this.maxSize = maxSize;
        }

        public V get(K key, java.util.function.Function<K, V> loader) {
            Entry<V> entry = map.get(key);
            if (entry != null && System.currentTimeMillis() < entry.validUntil()) {
                return entry.value();
            }
            Object lock = keyLocks.computeIfAbsent(key, k -> new Object());
            synchronized (lock) {
                // double-check：等锁期间同 key 可能已被其他线程回源
                entry = map.get(key);
                long now = System.currentTimeMillis();
                if (entry != null && now < entry.validUntil()) {
                    return entry.value();
                }
                try {
                    V value = loader.apply(key);
                    put(key, new Entry<>(value, now + ttlMillis, false));
                    return value;
                } catch (Exception ex) {
                    V stale = entry == null ? null : entry.value();
                    put(key, new Entry<>(stale, now + failureTtlMillis, true));
                    return stale;
                }
            }
        }

        /** 当前未过期（含失败降级窗口内）的值，不回源；无则返回 null */
        public V peek(K key) {
            Entry<V> entry = map.get(key);
            return entry != null && System.currentTimeMillis() < entry.validUntil() ? entry.value() : null;
        }

        private void put(K key, Entry<V> entry) {
            if (maxSize > 0 && !map.containsKey(key) && map.size() >= maxSize) {
                long now = System.currentTimeMillis();
                map.values().removeIf(e -> now >= e.validUntil());
                if (map.size() >= maxSize) {
                    map.clear();
                }
            }
            map.put(key, entry);
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
