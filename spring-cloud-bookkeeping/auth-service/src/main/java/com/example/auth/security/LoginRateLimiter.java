package com.example.auth.security;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流（进程内实现）。
 * 同一 key（用户名 + IP）在窗口期内失败达到阈值后锁定，窗口结束后自动解锁。
 * 单实例部署下足够；后续多实例部署应替换为 Redis 实现。
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MILLIS = 15 * 60 * 1000L;

    private final ConcurrentHashMap<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    /** 是否处于锁定状态（窗口期内失败次数达到阈值） */
    public boolean isBlocked(String key) {
        Deque<Long> timestamps = failures.get(key);
        if (timestamps == null) {
            return false;
        }
        long windowStart = System.currentTimeMillis() - WINDOW_MILLIS;
        synchronized (timestamps) {
            evictExpired(timestamps, windowStart);
            return timestamps.size() >= MAX_FAILURES;
        }
    }

    /** 记录一次登录失败 */
    public void recordFailure(String key) {
        Deque<Long> timestamps = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (timestamps) {
            evictExpired(timestamps, now - WINDOW_MILLIS);
            timestamps.addLast(now);
        }
    }

    /** 登录成功后清除失败记录 */
    public void clear(String key) {
        failures.remove(key);
    }

    private void evictExpired(Deque<Long> timestamps, long windowStart) {
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }
    }
}
