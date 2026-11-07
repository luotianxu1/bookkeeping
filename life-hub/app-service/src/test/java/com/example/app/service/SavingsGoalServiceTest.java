package com.example.app.service;

import com.example.app.dto.*;
import com.example.app.mapper.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SavingsGoalServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T16:30:00Z"), ZoneId.of("Asia/Shanghai"));
    private SavingsGoalService service(AccountingDatabase db) {
        return new SavingsGoalService(db.mapper(SavingsGoalMapper.class), db.mapper(SavingsGoalEntryMapper.class),
            db.mapper(AccountMapper.class), db.mapper(AccountTypeMapper.class), db.idempotency(), CLOCK);
    }
    private SavingsGoalRequest request(long accountId, String amount, LocalDate date) {
        return new SavingsGoalRequest(" 应急金 ", accountId, new BigDecimal(amount), date, "计划", UUID.randomUUID().toString());
    }
    private SavingsGoalEntryRequest entry(String type, String amount) {
        return new SavingsGoalEntryRequest(type, new BigDecimal(amount), LocalDate.now(CLOCK), "本月储蓄", UUID.randomUUID().toString());
    }
    private void money(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }

    @Test void progressUsesOnlyGoalEntriesAndNeverChangesCash() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts(); var service = service(db);
            var goal = db.tx.execute(s -> service.create(1L, request(1, "1500", LocalDate.of(2027, 1, 1))));
            assertEquals("应急金", goal.name());
            assertEquals(LocalDate.of(2026, 11, 7), goal.asOfDate());
            assertEquals(3, goal.monthsRemaining());
            money("0", goal.currentAmount()); money("500", goal.monthlyNeeded());
            db.tx.execute(s -> service.addEntry(1, goal.id(), entry("deposit", "1000")));
            var saved = service.list(1).get(0);
            money("1000", saved.currentAmount()); money("66.66", saved.progressPercent()); money("166.67", saved.monthlyNeeded());
            money("1000", db.balance(1));
            assertEquals(0, db.jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));
            var transfer = new TransactionRequest();
            transfer.setUserId(1L); transfer.setType("transfer"); transfer.setFromAccountId(2L); transfer.setToAccountId(1L);
            transfer.setAmount(new BigDecimal("500")); transfer.setOccurredAt(LocalDateTime.now(CLOCK));
            var transaction = db.tx.execute(s -> db.transactions().create(transfer));
            money("1000", service.list(1).get(0).currentAmount());
            assertEquals("saving", service.list(1).get(0).status());
            db.tx.execute(s -> db.transactions().delete(transaction.getId(), 1L));
            money("1000", service.list(1).get(0).currentAmount());
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.addEntry(1, goal.id(), entry("deposit", "500"))));
            db.tx.execute(s -> db.transactions().create(transfer));
            db.tx.execute(s -> service.addEntry(1, goal.id(), entry("deposit", "500")));
            assertEquals("completed", service.list(1).get(0).status());
            money("0", service.list(1).get(0).monthlyNeeded());
            db.tx.execute(s -> service.addEntry(1, goal.id(), entry("withdrawal", "100")));
            money("1400", service.list(1).get(0).currentAmount());
            assertEquals("saving", service.list(1).get(0).status());
            money("1500", db.balance(1));
        }
    }

    @Test void goalsOnSameAccountHaveIndependentAmountsAndHistory() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts(); var service = service(db);
            var first = db.tx.execute(s -> service.create(1L, request(1, "600", null)));
            var second = db.tx.execute(s -> service.create(1L, request(1, "2000", null)));
            db.tx.execute(s -> service.addEntry(1, first.id(), entry("deposit", "600")));
            db.tx.execute(s -> service.addEntry(1, second.id(), entry("deposit", "200")));
            var rows = service.list(1);
            money("200", rows.get(0).currentAmount()); money("10", rows.get(0).progressPercent());
            money("600", rows.get(1).currentAmount()); assertEquals("completed", rows.get(1).status());
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.addEntry(1, second.id(), entry("withdrawal", "201"))));
            db.tx.execute(s -> service.update(1L, second.id(), request(2, "2000", null)));
            var movedBack = db.tx.execute(s -> service.update(1L, second.id(), request(1, "2000", null)));
            money("200", movedBack.currentAmount());
            assertThrows(IllegalStateException.class, () -> db.tx.execute(s -> {
                service.delete(1, first.id()); throw new IllegalStateException("rollback");
            }));
            assertEquals(1, service.entries(1, first.id(), null).entries().size());
            db.tx.executeWithoutResult(s -> service.delete(1L, first.id()));
            assertEquals(second.id(), service.list(1L).get(0).id());
            assertEquals(1, service.list(1L).size());
            assertEquals(1, service.entries(1, second.id(), null).entries().size());
            assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM savings_goal_entries", Integer.class));
            money("1000", db.balance(1));
        }
    }

    @Test void dateCurrencyAndAccountAvailabilityPreserveRecordedProgress() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts(); var service = service(db);
            var goal = db.tx.execute(s -> service.create(1L, request(1, "1500", LocalDate.now(CLOCK))));
            assertEquals(1, goal.monthsRemaining()); money("1500", goal.monthlyNeeded());
            db.tx.execute(s -> service.update(1L, goal.id(), request(1, "1500", LocalDate.of(2026, 11, 6))));
            assertEquals("overdue", service.list(1L).get(0).status()); assertNull(service.list(1L).get(0).monthlyNeeded());
            db.jdbc.update("UPDATE accounts SET currency_code='USD' WHERE id=2");
            var updated = db.tx.execute(s -> service.update(1L, goal.id(), new SavingsGoalRequest("旅行", 2L, new BigDecimal("1500"), null, null, null)));
            assertEquals("USD", updated.currencyCode()); assertNull(updated.targetDate()); assertNull(updated.remark());
            db.tx.execute(s -> service.addEntry(1, goal.id(), entry("deposit", "300")));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.update(1L, goal.id(), request(1, "1500", null))));
            db.jdbc.update("UPDATE accounts SET current_balance=-20, status='archived' WHERE id=2");
            var unavailable = service.list(1L).get(0);
            assertFalse(unavailable.accountAvailable()); money("300", unavailable.currentAmount()); money("20", unavailable.progressPercent());
            db.tx.execute(s -> service.addEntry(1, goal.id(), entry("withdrawal", "300")));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.update(1L, goal.id(), request(1, "1500", null))));
        }
    }

    @Test void validationOwnershipAndIdempotencyProtectEntries() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts(); var service = service(db); var request = request(1, "2000", null);
            var goal = db.tx.execute(s -> service.create(1L, request));
            assertEquals(goal.id(), db.tx.execute(s -> service.create(1L, request)).id());
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.create(2, request(2, "2000", null))));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.create(1, request(3, "2000", null))));
            var deposit = entry("deposit", "200");
            var created = db.tx.execute(s -> service.addEntry(1, goal.id(), deposit));
            assertEquals(created.id(), db.tx.execute(s -> service.addEntry(1, goal.id(), deposit)).id());
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.addEntry(1, goal.id(),
                new SavingsGoalEntryRequest("deposit", new BigDecimal("300"), deposit.occurredOn(), deposit.remark(), deposit.requestId()))));
            var otherGoal = db.tx.execute(s -> service.create(1, request(1, "2000", null)));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.addEntry(1, otherGoal.id(), deposit)));
            assertThrows(IllegalArgumentException.class, () -> service.entries(2, goal.id(), null));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.addEntry(2, goal.id(), deposit)));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.update(2, goal.id(), request(2, "2000", null))));
            assertThrows(IllegalArgumentException.class, () -> db.tx.executeWithoutResult(s -> service.delete(2, goal.id())));
            assertTrue(service.list(2).isEmpty());
            for (String amount : new String[]{"0", "-1", "0.001", "10000000000000000"}) {
                assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.create(1, request(2, amount, null))));
                assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.addEntry(1, goal.id(), entry("deposit", amount))));
            }
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.addEntry(1, goal.id(), entry("invalid", "1"))));
            assertThrows(IllegalArgumentException.class, () -> db.tx.execute(s -> service.addEntry(1, goal.id(),
                new SavingsGoalEntryRequest("deposit", BigDecimal.ONE, LocalDate.now(CLOCK).plusDays(1), null, null))));
            assertThrows(IllegalStateException.class, () -> db.tx.execute(s -> {
                service.addEntry(1, goal.id(), entry("deposit", "300")); throw new IllegalStateException("rollback");
            }));
            assertEquals(1, service.entries(1, goal.id(), null).entries().size()); money("200", service.list(1).get(1).currentAmount());
        }
    }

    @Test void concurrentRetriesAndWithdrawalsCannotDuplicateOrOverdraw() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts(); var service = service(db); var executor = Executors.newFixedThreadPool(2);
            try {
                var request = request(1, "2000", null);
                Callable<Long> create = () -> db.tx.execute(s -> service.create(1, request)).id();
                var firstGoal = executor.submit(create); var secondGoal = executor.submit(create);
                long id = firstGoal.get(10, TimeUnit.SECONDS); assertEquals(id, secondGoal.get(10, TimeUnit.SECONDS));
                var deposit = entry("deposit", "100");
                Callable<Long> submit = () -> db.tx.execute(s -> service.addEntry(1, id, deposit)).id();
                var first = executor.submit(submit); var second = executor.submit(submit);
                assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
                Callable<Boolean> withdraw = () -> {
                    try { db.tx.execute(s -> service.addEntry(1, id, entry("withdrawal", "80"))); return true; }
                    catch (IllegalArgumentException expected) { return false; }
                };
                var one = executor.submit(withdraw); var two = executor.submit(withdraw);
                assertNotEquals(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
                money("20", service.list(1).get(0).currentAmount());
                assertEquals(2, service.entries(1, id, null).entries().size());
                assertEquals(3, db.jdbc.queryForObject("SELECT COUNT(*) FROM accounting_requests", Integer.class));
            } finally { executor.shutdownNow(); }
        }
    }

    @Test void historyPaginationDoesNotRepeatOrSkipEntries() throws Exception {
        try (var db = new AccountingDatabase()) {
            db.seedCashAccounts(); var service = service(db);
            var goal = db.tx.execute(s -> service.create(1, request(1, "2000", null)));
            for (int i=0; i<51; i++) db.tx.execute(s -> service.addEntry(1, goal.id(), entry("deposit", "1")));
            var first = service.entries(1, goal.id(), null);
            assertEquals(50, first.entries().size()); assertNotNull(first.nextBeforeId());
            var second = service.entries(1, goal.id(), first.nextBeforeId());
            assertEquals(1, second.entries().size()); assertNull(second.nextBeforeId());
            assertTrue(second.entries().get(0).id() < first.nextBeforeId());
            money("51", service.list(1).get(0).currentAmount());
        }
    }
}
