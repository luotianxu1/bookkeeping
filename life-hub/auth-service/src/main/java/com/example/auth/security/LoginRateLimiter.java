package com.example.auth.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 单实例登录限流：账号与连接 IP 分别限制，在途尝试占用额度，防止并发绕过。 */
@Component
public class LoginRateLimiter {
    private static final long WINDOW_MILLIS = 15 * 60 * 1000L;
    private static final int MAX_TRACKED_KEYS = 10_000;
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;

    public LoginRateLimiter() { this(Clock.systemUTC()); }
    LoginRateLimiter(Clock clock) { this.clock = clock; }

    private static final class Window {
        final ArrayDeque<Long> failures = new ArrayDeque<>();
        int inFlight;
    }

    public static String normalizeAccount(String account) {
        return account.trim().toLowerCase(Locale.ROOT);
    }

    /** 返回 null 表示已限流；调用者必须在 finally 中完成尝试，释放在途额度。 */
    public synchronized Attempt tryAcquire(String account, String ip) {
        long cutoff = clock.millis() - WINDOW_MILLIS;
        windows.values().forEach(window -> {
            while (!window.failures.isEmpty() && window.failures.peekFirst() <= cutoff) window.failures.removeFirst();
        });
        windows.entrySet().removeIf(entry -> entry.getValue().inFlight == 0 && entry.getValue().failures.isEmpty());
        String accountKey = "account:" + normalizeAccount(account);
        String ipKey = "ip:" + ip;
        int newKeys = (windows.containsKey(accountKey) ? 0 : 1) + (windows.containsKey(ipKey) ? 0 : 1);
        if (windows.size() + newKeys > MAX_TRACKED_KEYS) return null;
        Window accountWindow = windows.computeIfAbsent(accountKey, key -> new Window());
        Window ipWindow = windows.computeIfAbsent(ipKey, key -> new Window());
        if (accountWindow.failures.size() + accountWindow.inFlight >= 5
            || ipWindow.failures.size() + ipWindow.inFlight >= 30) return null;
        accountWindow.inFlight++;
        ipWindow.inFlight++;
        return new Attempt(accountWindow, ipWindow);
    }

    public final class Attempt {
        private final Window account;
        private final Window ip;
        private boolean completed;
        private Attempt(Window account, Window ip) { this.account = account; this.ip = ip; }

        public void complete(boolean success) {
            synchronized (LoginRateLimiter.this) {
                if (completed) return;
                completed = true;
                account.inFlight--;
                ip.inFlight--;
                if (success) {
                    account.failures.clear();
                } else {
                    long now = clock.millis();
                    account.failures.addLast(now);
                    ip.failures.addLast(now);
                }
            }
        }
    }
}
