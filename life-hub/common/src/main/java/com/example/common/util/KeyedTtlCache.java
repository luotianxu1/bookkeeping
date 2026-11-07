package com.example.common.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * 统一 Keyed TTL 缓存：TTL + 固定分段锁 single-flight + 失败降级 + LRU 容量限制。
 * 同 key 的回源串行，哈希落在同一分段的不同 key 也会串行；锁数量不随历史 key 增长。
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
    // 固定数量的锁，不随访问过的 key 数增长。map 锁只保护内存操作，不包裹回源。
    private final Object[] stripes = new Object[256];
    private final Map<K, Entry<V>> map = new LinkedHashMap<>(16, 0.75f, true);
    private final LongSupplier clock;

    private record Entry<V>(V value, long validUntil) {
    }

    public KeyedTtlCache(long ttlMillis, long failureTtlMillis, int maxSize) {
        this(ttlMillis, failureTtlMillis, maxSize, System::currentTimeMillis);
    }

    public KeyedTtlCache(long ttlMillis, long failureTtlMillis, int maxSize, LongSupplier clock) {
        this.ttlMillis = ttlMillis;
        this.failureTtlMillis = failureTtlMillis;
        this.maxSize = maxSize;
        this.clock = clock;
        for (int i = 0; i < stripes.length; i++) stripes[i] = new Object();
    }

    public V get(K key, Function<K, V> loader) {
        Objects.requireNonNull(key);
        Entry<V> entry = entry(key);
        if (entry != null && clock.getAsLong() < entry.validUntil()) {
            return entry.value();
        }
        int hash = key.hashCode();
        Object lock = stripes[(hash ^ (hash >>> 16)) & (stripes.length - 1)];
        synchronized (lock) {
            // double-check：等锁期间同 key 可能已被其他线程回源
            entry = entry(key);
            long now = clock.getAsLong();
            if (entry != null && now < entry.validUntil()) {
                return entry.value();
            }
            try {
                V value = Objects.requireNonNull(loader.apply(key), "cache loader returned null");
                put(key, new Entry<>(value, clock.getAsLong() + ttlMillis));
                return value;
            } catch (Exception ex) {
                V stale = entry == null ? null : entry.value();
                put(key, new Entry<>(stale, clock.getAsLong() + failureTtlMillis));
                return stale;
            }
        }
    }

    /** 当前未过期（含失败降级窗口内）的值，不回源；无则返回 null */
    public V peek(K key) {
        Entry<V> entry = entry(key);
        return entry != null && clock.getAsLong() < entry.validUntil() ? entry.value() : null;
    }

    private Entry<V> entry(K key) {
        synchronized (map) {
            return map.get(key);
        }
    }

    private void put(K key, Entry<V> entry) {
        synchronized (map) {
            map.put(key, entry);
            // 只淘汰最久未访问的条目，避免整表清空引起集中回源。
            while (maxSize > 0 && map.size() > maxSize) {
                var iterator = map.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }
    }
}
