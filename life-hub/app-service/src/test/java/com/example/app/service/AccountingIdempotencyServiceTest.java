package com.example.app.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AccountingIdempotencyServiceTest {
    private record Reply(int id, String status) {}

    @Test void replaysOriginalResponseAndRejectsPayloadReuse() throws Exception {
        try (var db = new AccountingDatabase()) {
            var service = db.idempotency();
            var calls = new AtomicInteger();
            Reply first = db.tx.execute(status -> service.execute(1L, "test", "request-123456789", Map.of("amount", 1.00), Reply.class,
                () -> { calls.incrementAndGet(); return new Reply(7, "created"); }));
            Reply replay = db.tx.execute(status -> service.execute(1L, "test", "request-123456789", Map.of("amount", 1), Reply.class,
                () -> { calls.incrementAndGet(); return new Reply(8, "wrong"); }));
            assertEquals(first, replay);
            assertEquals(1, calls.get());
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(status -> service.execute(1L, "test", "request-123456789", Map.of("amount", 2), Reply.class,
                () -> new Reply(9, "wrong"))));
        }
    }

    @Test void rollbackRemovesKeySoClientCanRetry() throws Exception {
        try (var db = new AccountingDatabase()) {
            var service = db.idempotency();
            assertThrows(IllegalStateException.class, () -> db.tx.execute(status -> service.execute(1L, "test", "request-223456789", Map.of("amount", 1), Reply.class,
                () -> { throw new IllegalStateException("downstream"); })));
            Reply reply = db.tx.execute(status -> service.execute(1L, "test", "request-223456789", Map.of("amount", 1), Reply.class,
                () -> new Reply(10, "created")));
            assertEquals(10, reply.id());
        }
    }
}
