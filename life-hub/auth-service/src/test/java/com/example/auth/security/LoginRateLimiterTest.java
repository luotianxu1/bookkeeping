package com.example.auth.security;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest {
    @Test void whitespaceCaseAndChangingIpDoNotResetAccountFailures() {
        var limiter = new LoginRateLimiter();
        for (int i = 0; i < 5; i++) {
            var attempt = limiter.tryAcquire(" User1 ", "ip" + i);
            assertNotNull(attempt);
            attempt.complete(false);
        }
        assertNull(limiter.tryAcquire("user1", "another-ip"));
        assertNull(limiter.tryAcquire(" user1\t", "another-ip"));
    }

    @Test void rotatingAccountsCannotBypassIpLimitAndSuccessDoesNotEraseIpFailures() {
        var limiter = new LoginRateLimiter();
        for (int i = 0; i < 29; i++) limiter.tryAcquire("user" + i, "ip").complete(false);
        limiter.tryAcquire("valid-user", "ip").complete(true);
        limiter.tryAcquire("last-user", "ip").complete(false);
        assertNull(limiter.tryAcquire("fresh-user", "ip"));
        assertNotNull(limiter.tryAcquire("fresh-user", "another-ip"));
    }

    @Test void inFlightAttemptsReserveQuotaAndCompletionIsIdempotent() {
        var limiter = new LoginRateLimiter();
        var pending = new ArrayList<LoginRateLimiter.Attempt>();
        for (int i = 0; i < 5; i++) pending.add(limiter.tryAcquire("user", "ip" + i));
        assertTrue(pending.stream().allMatch(java.util.Objects::nonNull));
        assertNull(limiter.tryAcquire("user", "other-ip"));
        pending.get(0).complete(true);
        pending.get(0).complete(true);
        assertNotNull(limiter.tryAcquire("user", "other-ip"));
        assertNull(limiter.tryAcquire("user", "another-ip"));
    }

    @Test void expiredFailuresAllowRetry() {
        var now = new java.util.concurrent.atomic.AtomicLong(0);
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        };
        var limiter = new LoginRateLimiter(clock);
        for (int i = 0; i < 5; i++) limiter.tryAcquire("user", "ip").complete(false);
        assertNull(limiter.tryAcquire("user", "ip"));
        now.set(15 * 60 * 1000);
        assertNotNull(limiter.tryAcquire("user", "ip"));
    }
}
