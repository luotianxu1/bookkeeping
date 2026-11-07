package com.example.common.security;

import com.example.common.util.KeyedTtlCache;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;

/** 认证状态最多信任 30 秒；失败缓存只能抑制回源，不能延长认证有效期。 */
public final class UserStatusCache {
    private static final long TRUST_MILLIS = 30_000;
    record Verified(boolean active, long expiresAt) {}
    private final LongSupplier clock;
    private final KeyedTtlCache<Long, Verified> cache;

    public UserStatusCache() {
        this(System::currentTimeMillis, new KeyedTtlCache<>(TRUST_MILLIS, 5_000, 10_000));
    }

    UserStatusCache(LongSupplier clock, KeyedTtlCache<Long, Verified> cache) {
        this.clock = clock;
        this.cache = cache;
    }

    public boolean isActive(long userId, LongPredicate loader) {
        Verified verified = cache.get(userId, id -> {
            boolean active = loader.test(id);
            return new Verified(active, clock.getAsLong() + TRUST_MILLIS);
        });
        if (verified == null || clock.getAsLong() >= verified.expiresAt()) {
            throw new UserStatusUnavailableException();
        }
        return verified.active();
    }
}
