package com.example.auth.security;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注册尝试限流（进程内滑动窗口）。
 * 同一 IP 在窗口期内注册尝试（无论成败）达到阈值后拒绝，防止批量枚举用户名与灌垃圾账号。
 * 单实例部署下足够；后续多实例部署应替换为 Redis 实现。
 */
@Component
public class RegisterRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MILLIS = 15 * 60 * 1000L;

    private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        Deque<Long> timestamps = attempts.get(key);
        if (timestamps == null) {
            return false;
        }
        long windowStart = System.currentTimeMillis() - WINDOW_MILLIS;
        synchronized (timestamps) {
            evictExpired(timestamps, windowStart);
            return timestamps.size() >= MAX_ATTEMPTS;
        }
    }

    /** 记录一次注册尝试（无论成败都计数） */
    public void record(String key) {
        Deque<Long> timestamps = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (timestamps) {
            evictExpired(timestamps, now - WINDOW_MILLIS);
            timestamps.addLast(now);
        }
    }

    private void evictExpired(Deque<Long> timestamps, long windowStart) {
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }
    }
}
