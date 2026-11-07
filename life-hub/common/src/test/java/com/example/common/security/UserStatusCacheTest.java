package com.example.common.security;

import com.example.common.util.KeyedTtlCache;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class UserStatusCacheTest {
    @Test void statusExpiresEvenWhenCacheWouldServeFailureStaleValue() {
        AtomicLong clock = new AtomicLong();
        var cache = new UserStatusCache(clock::get, new KeyedTtlCache<Long, UserStatusCache.Verified>(30_000, 5_000, 10, clock::get));
        AtomicInteger loads = new AtomicInteger();
        assertTrue(cache.isActive(1, id -> { loads.incrementAndGet(); return true; }));
        clock.set(30_000);
        assertThrows(UserStatusUnavailableException.class, () -> cache.isActive(1, id -> {
            loads.incrementAndGet();
            throw new IllegalStateException("down");
        }));
        assertEquals(2, loads.get());
        clock.set(34_999);
        assertThrows(UserStatusUnavailableException.class, () -> cache.isActive(1, id -> fail("retried during failure cache")));
        clock.set(35_000);
        assertFalse(cache.isActive(1, id -> false));
        assertTrue(cache.isActive(2, id -> true));
    }

    @Test void inactiveResultIsCachedOnlyForTrustWindow() {
        AtomicLong clock = new AtomicLong();
        var cache = new UserStatusCache(clock::get, new KeyedTtlCache<Long, UserStatusCache.Verified>(30_000, 5_000, 10, clock::get));
        AtomicInteger loads = new AtomicInteger();
        assertFalse(cache.isActive(2, id -> { loads.incrementAndGet(); return false; }));
        assertFalse(cache.isActive(2, id -> { loads.incrementAndGet(); return true; }));
        assertEquals(1, loads.get());
        clock.set(30_000);
        assertTrue(cache.isActive(2, id -> { loads.incrementAndGet(); return true; }));
        assertEquals(2, loads.get());
    }
}
