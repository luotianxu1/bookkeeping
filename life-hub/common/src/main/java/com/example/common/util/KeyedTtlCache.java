package com.example.common.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 统一 Keyed TTL 缓存：TTL + 按 key 串行 single-flight + 失败降级 + maxSize。
 * <p>
 * - 失败降级：loader 抛异常时，有旧值则在 failureTtl 窗口内回吐旧值；无旧值则记 null 负条目，
 *   窗口过后自动重试（避免故障期间高频回源打挂下游）。
 * - 约定：loader 失败用异常表达、正常路径不得返回 null；
 *   {@link #get} 返回 null 表示失败窗口内无可用值，由调用方兜底/抛错。
 */
public class KeyedTtlCache<K, V> {

    private final long ttlMillis;
    private final long failureTtlMillis;
    private final int maxSize;
    private final ConcurrentHashMap<K, Entry<V>> map = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K, Object> keyLocks = new ConcurrentHashMap<>();

    private record Entry<V>(V value, long validUntil, boolean stale) {
    }

    public KeyedTtlCache(long ttlMillis, long failureTtlMillis, int maxSize) {
        this.ttlMillis = ttlMillis;
        this.failureTtlMillis = failureTtlMillis;
        this.maxSize = maxSize;
    }

    public V get(K key, Function<K, V> loader) {
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
