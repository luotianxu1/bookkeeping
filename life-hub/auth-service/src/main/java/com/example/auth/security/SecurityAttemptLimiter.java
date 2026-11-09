package com.example.auth.security;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/** 安全操作独立限流：按账号与实际连接 IP 双重限制，成功尝试同样计数。 */
@Component
public class SecurityAttemptLimiter {
    private record Window(long expiresAt, int count) {}
    private final Map<String, Window> windows = new HashMap<>();
    public synchronized boolean allow(String scope, String account, String ip) {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        if (windows.size() >= 10000) return false;
        return consume(scope + ":ip:" + ip, 30, now) && consume(scope + ":account:" + account, 5, now);
    }
    private boolean consume(String key, int limit, long now) {
        Window window = windows.getOrDefault(key, new Window(now + 15 * 60 * 1000, 0));
        if (window.count() >= limit) return false;
        windows.put(key, new Window(window.expiresAt(), window.count() + 1));
        return true;
    }
}
