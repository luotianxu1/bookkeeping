package com.example.common.util;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class KeyedTtlCacheTest {
    @Test void ttlStartsAfterSlowLoaderFinishes() {
        AtomicLong clock = new AtomicLong();
        var cache = new KeyedTtlCache<String, String>(100, 20, 2, clock::get);
        assertEquals("value", cache.get("a", key -> { clock.addAndGet(500); return "value"; }));
        clock.addAndGet(99);
        assertEquals("value", cache.get("a", key -> fail("loader ran before TTL elapsed")));
        clock.incrementAndGet();
        assertEquals("new", cache.get("a", key -> "new"));
    }

    @Test void failedRefreshServesOldValueForFullFailureWindow() {
        AtomicLong clock = new AtomicLong();
        var cache = new KeyedTtlCache<String, String>(100, 20, 2, clock::get);
        cache.get("a", key -> "old");
        clock.set(100);
        assertEquals("old", cache.get("a", key -> { clock.addAndGet(500); throw new IllegalStateException(); }));
        clock.set(619);
        assertEquals("old", cache.get("a", key -> fail("retried during failure window")));
        clock.set(620);
        assertEquals("new", cache.get("a", key -> "new"));
    }

    @Test void negativeCacheSuppressesRepeatedFailuresAndThenRetries() {
        AtomicLong clock = new AtomicLong();
        var cache = new KeyedTtlCache<String, String>(100, 20, 2, clock::get);
        assertNull(cache.get("a", key -> { throw new IllegalStateException(); }));
        assertNull(cache.get("a", key -> fail("retried too soon")));
        clock.set(20);
        assertEquals("recovered", cache.get("a", key -> "recovered"));
    }

    @Test void capacityEvictsOnlyLeastRecentlyUsedEntry() {
        var cache = new KeyedTtlCache<Integer, String>(10000, 100, 2);
        cache.get(1, key -> "one");
        cache.get(2, key -> "two");
        assertEquals("one", cache.peek(1));
        cache.get(3, key -> "three");
        assertEquals("one", cache.peek(1));
        assertNull(cache.peek(2));
        assertEquals("three", cache.peek(3));
    }

    @Test void concurrentRequestsForSameKeyLoadOnce() throws Exception {
        var cache = new KeyedTtlCache<String, String>(10000, 100, 2);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var loads = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            Callable<String> task = () -> cache.get("a", key -> {
                loads.incrementAndGet();
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                return "value";
            });
            var first = executor.submit(task);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var others = new java.util.ArrayList<Future<String>>();
            for (int i = 0; i < 7; i++) others.add(executor.submit(task));
            release.countDown();
            assertEquals("value", first.get(5, TimeUnit.SECONDS));
            for (var future : others) assertEquals("value", future.get(5, TimeUnit.SECONDS));
            assertEquals(1, loads.get());
        } finally { release.countDown(); executor.shutdownNow(); }
    }
}
